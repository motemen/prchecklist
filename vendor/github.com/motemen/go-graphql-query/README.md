# graphqlquery

[![GoDoc](https://godoc.org/github.com/motemen/go-graphql-query?status.svg)](https://godoc.org/github.com/motemen/go-graphql-query)

Package graphqlquery generates GraphQL queries from the result structs.

## Examples

### Build

```go
package main

import (
    "fmt"

    "github.com/motemen/go-graphql-query"
)

// A struct simply generates GraphQL query generating the result
// suitable for the struct
type simpleExample struct {
    Hero struct {
        Name    string
        Friends []struct {
            Name string
        }
    }
}

type complexExample struct {
    Hero struct {
        Name string

        // Use embedding and "..." tag to build inline fragments query
        DroidFields `graphql:"... on Droid"`
        // or "..." for a field
        Height int `graphql:"... on Human"`

        Friends []struct {
            Name string
        }   `graphql:"@include(if: $withFriends)"` // Directives
    }   `graphql:"(episode: $ep)"` // Use "(..)" tag to specify arguments

    EmpireHero struct {
        // Arguments also can be specified by the special field GraphQLArguments
        GraphQLArguments struct {
            // Arguments in GraphQLArguments are automatically shown in the query arguments
            Episode Episode `graphql:"$ep"`
        }
        Name string
    }   `graphql:"alias=hero"` // use "alias=" tag to use alias

    // GraphQLArguments at toplevel stands for query arguments
    GraphQLArguments struct {
        // Should include arguments appeared in struct tags
        // Variables appeared in GraphQLArguments ($ep in EmpireHero) are
        // automatically shown in the query arguments
        WithFriends bool `graphql:"$withFriends,notnull"`
    }
}

type DroidFields struct {
    PrimaryFunction string
}

// Types starting with capital letter are treated as custom types
// TODO: more configurable way to assign Go types to GraphQL type names
type Episode string

func main() {
    s, _ := graphqlquery.Build(&simpleExample{})
    c, _ := graphqlquery.Build(&complexExample{})
    fmt.Println(string(s))
    fmt.Println(string(c))
}
```

Output:

```
query {
  hero {
    name
    friends {
      name
    }
  }
}
query($ep: Episode, $withFriends: Boolean!) {
  hero(episode: $ep) {
    name
    ... on Droid {
      primaryFunction
    }
    ... on Human {
      height
    }
    friends @include(if: $withFriends) {
      name
    }
  }
  empireHero: hero(episode: $ep) {
    name
  }
}
```

## Author

motemen <https://motemen.github.io/>
