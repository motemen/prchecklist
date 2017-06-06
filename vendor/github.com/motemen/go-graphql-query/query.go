// Package graphqlquery generates GraphQL queries from the result structs.
package graphqlquery

import (
	"bytes"
	"fmt"
	"io"
	"reflect"
	"sort"
	"strings"
	"unicode"
)

const argumentsFieldName = "GraphQLArguments"

// Build makes GraphQL query suitable for q, which is also
// a result JSON object for the GraphQL result JSON.
// See the example.
func Build(q interface{}) ([]byte, error) {
	b := &builder{
		query: q,
	}

	return b.build()
}

type builder struct {
	query interface{}
	args  []argSpec
}

type argSpec struct {
	field reflect.StructField
}

func (a argSpec) variableName() string {
	return getTagWithPrefix(a.field, "$")
}

func getTag(field reflect.StructField, n int) string {
	tags := parseTags(field.Tag.Get("graphql"))
	if len(tags) > n {
		return tags[n]
	}
	return ""
}

func getTagWithPrefix(field reflect.StructField, prefix string) string {
	tags := parseTags(field.Tag.Get("graphql"))
	for _, tag := range tags {
		if strings.HasPrefix(tag, prefix) {
			return tag
		}
	}

	return ""
}

func getTagNamed(field reflect.StructField, name string) string {
	s := getTagWithPrefix(field, name+"=")
	if s != "" {
		s = s[len(name)+1:]
	}
	return s
}

// parseTags parses struct tags, with parenthesis aware.
// see TestParseTags.
func parseTags(s string) []string {
	tags := strings.Split(s, ",")
TAGS:
	for i := 0; i < len(tags); i++ {
		if tags[i] == "" {
			continue
		}

		if tags[i][0] != '(' {
			continue
		}

		for j := i; j < len(tags); j++ {
			if tags[j] == "" {
				continue
			}

			if tags[j][len(tags[j])-1] != ')' {
				continue
			}
			if j == i {
				continue TAGS
			} else {
				for k := i + 1; k <= j; k++ {
					tags[i] += "," + tags[k]
				}
				tags = append(tags[0:i+1], tags[j+1:]...)
			}
		}
	}

	return tags
}

// queryArguments builds arguments for the query itself.
//
// Arguments for each fields are collected during toString() and ones with
// variable name assigned are included in the resulting arguments.
//
// Also, the special field named GraphQLArguments at the toplevel of the struct
// query is concidered as the arguments for the GraphQL query.
//
// In both ways, the types of the fields are treated as the types of the
// arguments in the GraphQL query.
func (b *builder) queryArguments() (string, error) {
	args := []string{}

	if argsStructType := argumentsStructType(reflect.TypeOf(b.query)); argsStructType != nil {
		for i := 0; i < argsStructType.NumField(); i++ {
			field := argsStructType.Field(i)
			b.args = append(b.args, argSpec{field})
		}
	}

	for _, spec := range b.args {
		varName := spec.variableName()
		if varName == "" {
			continue
		}
		typeName, err := b.typeName(spec.field.Type)
		if err != nil {
			return "", err
		}
		param := fmt.Sprintf("%s: %s", varName, typeName)
		if getTag(spec.field, 1) == "notnull" {
			param += "!"
		}
		args = append(args, param)
	}

	if len(args) == 0 {
		return "", nil
	}

	sort.Strings(args)

	return "(" + strings.Join(args, ", ") + ")", nil
}

// typeName returns a type name in GraphQL representinge the given type rt.
//
// If the type is a named type and its name starts with capital, the names is
// used in GraphQL query.
//
// TODO: more configurable way to convert Go types to GraphQL types
//       maybe graphqlquery.RegisterType(&someType{}, "SomeType")?
func (b *builder) typeName(rt reflect.Type) (string, error) {
	if rt.Kind() == reflect.Ptr {
		rt = rt.Elem()
	}

	if unicode.IsUpper(rune(rt.Name()[0])) {
		return rt.Name(), nil
	}

	switch rt.Kind() {
	case reflect.Array, reflect.Slice:
		name, err := b.typeName(rt.Elem())
		return "[" + name + "]", err
	case reflect.Bool:
		return "Boolean", nil
	case reflect.Float32, reflect.Float64:
		return "Float", nil
	case reflect.Int, reflect.Int16, reflect.Int32, reflect.Int64, reflect.Int8:
		return "Int", nil
	case reflect.Ptr:
		return b.typeName(rt.Elem())
	case reflect.String:
		return "String", nil
	}

	return "", fmt.Errorf("could not find type name for %s", rt.Name())
}

func (b *builder) build() ([]byte, error) {
	var buf bytes.Buffer
	fmt.Fprintf(&buf, " {\n")

	err := b.toString(&buf, reflect.TypeOf(b.query), 1)
	if err != nil {
		return nil, err
	}

	fmt.Fprintf(&buf, "}")

	args, err := b.queryArguments()
	if err != nil {
		return nil, err
	}

	return append([]byte("query"+args), buf.Bytes()...), nil
}

func (b *builder) writeStructField(w io.Writer, depth int, field reflect.StructField) error {
	var (
		name      = b.nameForField(field)
		args      = b.argsStringForField(field)
		directive = b.directiveStringForField(field)
		fragment  = getTagWithPrefix(field, "...")
	)

	if directive != "" {
		directive = " " + directive
	}

	if fragment != "" {
		if field.Anonymous {
			// The embedded struct fields are expanded in the fragment body
			//
			//   ... on Human {
			fmt.Fprintf(w, "%s%s%s {\n", strings.Repeat(" ", depth*2), fragment, directive)
		} else {
			// The struct fields with its name are shown in the fragment
			//
			//   ... on Human {
			//     friend {
			//       name
			//     }
			//   }
			fmt.Fprintf(w, "%s%s%s {\n", strings.Repeat(" ", depth*2), fragment, directive)
			copyField := field
			copyField.Tag = ""
			err := b.writeStructField(w, depth+1, copyField)
			fmt.Fprintf(w, "%s}\n", strings.Repeat(" ", depth*2))
			return err
		}
	} else {
		// hero(ep: EMPIRE) {
		fmt.Fprintf(w, "%s%s%s%s {\n", strings.Repeat(" ", depth*2), name, args, directive)
	}

	err := b.toString(w, field.Type, depth+1)

	fmt.Fprintf(w, "%s}\n", strings.Repeat(" ", depth*2))

	return err
}

func (b *builder) nameForField(field reflect.StructField) string {
	name := field.Tag.Get("json")
	if p := strings.Index(name, ","); p != -1 {
		name = name[0:p]
	}

	if name == "" {
		name = b.toName(field.Name)
	}

	alias := getTagNamed(field, "alias")
	if alias != "" {
		name = name + ": " + alias
	}

	return name
}

func (b *builder) directiveStringForField(field reflect.StructField) string {
	return getTagWithPrefix(field, "@")
}

// argsStringForField returns GraphQL arguments string for given struct field.
//
// If the field is a struct and has a special struct field named GraphQLArguments,
// the struct's fields are treated as arguments.
//
// Otherwise, struct tag of "(...)" form is use as arguments as is.
// eg. `graphql:"(episode: EMPIRE, id: \"1\")"`
func (b *builder) argsStringForField(field reflect.StructField) string {
	if argsStructType := argumentsStructType(field.Type); argsStructType != nil {
		aa := []string{}
		for i := 0; i < argsStructType.NumField(); i++ {
			field := argsStructType.Field(i)
			b.args = append(b.args, argSpec{field})
			aa = append(aa, fmt.Sprintf("%s: %v", b.toName(field.Name), getTag(field, 0)))
		}
		sort.Strings(aa)

		return "(" + strings.Join(aa, ", ") + ")"
	}

	if tag := getTagWithPrefix(field, "("); tag != "" {
		return tag
	}

	return ""
}

func (b *builder) writeSimpleField(w io.Writer, depth int, field reflect.StructField) {
	var (
		name      = b.nameForField(field)
		args      = b.argsStringForField(field)
		directive = b.directiveStringForField(field)
		fragment  = getTagWithPrefix(field, "...")
	)
	if fragment != "" {
		fmt.Fprintf(w, "%s%s {\n", strings.Repeat(" ", depth*2), fragment)
		fmt.Fprintf(w, "%s%s%s%s\n", strings.Repeat(" ", (depth+1)*2), name, args, directive)
		fmt.Fprintf(w, "%s}\n", strings.Repeat(" ", depth*2))
	} else {
		fmt.Fprintf(w, "%s%s%s%s\n", strings.Repeat(" ", depth*2), name, args, directive)
	}
}

func (b *builder) toString(w io.Writer, rt reflect.Type, depth int) error {
	origType, rt := rt, reflectStructType(rt)
	if rt == nil {
		return fmt.Errorf("invalid type: %v", origType)
	}

	for i := 0; i < rt.NumField(); i++ {
		field := rt.Field(i)
		if field.Name == argumentsFieldName {
			continue
		}

		if ft := reflectStructType(field.Type); ft != nil {
			err := b.writeStructField(w, depth, field)
			if err != nil {
				return err
			}
		} else {
			b.writeSimpleField(w, depth, field)
		}
	}

	return nil
}

func (b *builder) toName(name string) string {
	for i, r := range name {
		if i == 0 {
			continue
		}
		if i == 1 && !unicode.IsUpper(r) {
			return strings.ToLower(name[0:i]) + name[i:]
		}
		if !unicode.IsUpper(r) {
			return strings.ToLower(name[0:i-1]) + name[i-1:]
		}
	}
	return strings.ToLower(name)
}

func reflectStructType(rt reflect.Type) reflect.Type {
	if rt == nil {
		return rt
	}

	for rt.Kind() == reflect.Ptr || rt.Kind() == reflect.Slice || rt.Kind() == reflect.Array {
		rt = rt.Elem()
	}
	if rt.Kind() == reflect.Struct {
		return rt
	}

	return nil
}

func argumentsStructType(rt reflect.Type) reflect.Type {
	if rt := reflectStructType(rt); rt != nil {
		if field, ok := rt.FieldByName(argumentsFieldName); ok {
			return reflectStructType(field.Type)
		}
	}

	return nil
}
