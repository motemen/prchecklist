module github.com/motemen/prchecklist/v2

go 1.25.0

require (
	cloud.google.com/go/datastore v1.21.0
	github.com/boltdb/bolt v1.3.1
	github.com/elazarl/go-bindata-assetfs v1.0.1
	github.com/garyburd/redigo v1.6.0
	github.com/golang/mock v1.6.0
	github.com/google/go-github/v85 v85.0.0
	github.com/gorilla/handlers v1.5.1
	github.com/gorilla/mux v1.8.0
	github.com/gorilla/schema v1.4.1
	github.com/gorilla/sessions v1.2.0
	github.com/motemen/go-graphql-query v0.0.0-20190808105856-1e064957a3ee
	github.com/motemen/go-loghttp v0.0.0-20170804080138-974ac5ceac27
	github.com/motemen/go-nuts v0.0.0-20190725124253-1d2432db96b0
	github.com/patrickmn/go-cache v2.1.0+incompatible
	github.com/pkg/errors v0.9.1
	github.com/stretchr/testify v1.11.1
	golang.org/x/oauth2 v0.36.0
	golang.org/x/sync v0.20.0
	golang.org/x/tools v0.44.0
	gopkg.in/yaml.v2 v2.3.0
)

require (
	cloud.google.com/go v0.121.6 // indirect
	cloud.google.com/go/auth v0.18.2 // indirect
	cloud.google.com/go/auth/oauth2adapt v0.2.8 // indirect
	cloud.google.com/go/compute/metadata v0.9.0 // indirect
	github.com/BurntSushi/toml v0.3.1 // indirect
	github.com/Songmu/gocredits v0.4.0 // indirect
	github.com/a-urth/go-bindata v0.0.0-20180209162145-df38da164efc // indirect
	github.com/cespare/reflex v0.3.2 // indirect
	github.com/cespare/xxhash/v2 v2.3.0 // indirect
	github.com/creack/pty v1.1.24 // indirect
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/felixge/httpsnoop v1.0.4 // indirect
	github.com/fsnotify/fsnotify v1.9.0 // indirect
	github.com/go-logr/logr v1.4.3 // indirect
	github.com/go-logr/stdr v1.2.2 // indirect
	github.com/golang/groupcache v0.0.0-20210331224755-41bb18bfe9da // indirect
	github.com/golang/protobuf v1.5.4 // indirect
	github.com/google/go-cmp v0.7.0 // indirect
	github.com/google/go-querystring v1.2.0 // indirect
	github.com/google/s2a-go v0.1.9 // indirect
	github.com/googleapis/enterprise-certificate-proxy v0.3.11 // indirect
	github.com/googleapis/gax-go/v2 v2.17.0 // indirect
	github.com/gorilla/securecookie v1.1.1 // indirect
	github.com/jstemmer/go-junit-report v0.9.1 // indirect
	github.com/kballard/go-shellquote v0.0.0-20180428030007-95032a82bc51 // indirect
	github.com/lestrrat-go/jspointer v0.0.0-20181205001929-82fadba7561c // indirect
	github.com/lestrrat-go/jsref v0.0.0-20211028120858-c0bcbb5abf20 // indirect
	github.com/lestrrat-go/option v1.0.0 // indirect
	github.com/lestrrat-go/pdebug v0.0.0-20210111095411-35b07dbf089b // indirect
	github.com/lestrrat-go/structinfo v0.0.0-20210312050401-7f8bd69d6acb // indirect
	github.com/lestrrat/go-jsschema v0.0.0-20181205002244-5c81c58ffcc3 // indirect
	github.com/motemen/go-generate-jsschema v0.0.0-20170921015939-f9efddabe75d // indirect
	github.com/ogier/pflag v0.0.1 // indirect
	github.com/pmezard/go-difflib v1.0.0 // indirect
	go.opencensus.io v0.24.0 // indirect
	go.opentelemetry.io/auto/sdk v1.2.1 // indirect
	go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc v0.61.0 // indirect
	go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp v0.61.0 // indirect
	go.opentelemetry.io/otel v1.44.0 // indirect
	go.opentelemetry.io/otel/metric v1.44.0 // indirect
	go.opentelemetry.io/otel/trace v1.44.0 // indirect
	golang.org/x/crypto v0.51.0 // indirect
	golang.org/x/lint v0.0.0-20241112194109-818c5a804067 // indirect
	golang.org/x/mod v0.35.0 // indirect
	golang.org/x/net v0.55.0 // indirect
	golang.org/x/sys v0.45.0 // indirect
	golang.org/x/text v0.37.0 // indirect
	golang.org/x/time v0.14.0 // indirect
	golang.org/x/xerrors v0.0.0-20200804184101-5ec99f83aff1 // indirect
	google.golang.org/api v0.264.0 // indirect
	google.golang.org/appengine v1.6.8 // indirect
	google.golang.org/genproto v0.0.0-20260128011058-8636f8732409 // indirect
	google.golang.org/genproto/googleapis/api v0.0.0-20260526163538-3dc84a4a5aaa // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20260526163538-3dc84a4a5aaa // indirect
	google.golang.org/grpc v1.83.1 // indirect
	google.golang.org/protobuf v1.36.11 // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
	honnef.co/go/tools v0.0.1-2020.1.3 // indirect
)

tool (
	github.com/Songmu/gocredits/cmd/gocredits
	github.com/a-urth/go-bindata/go-bindata
	github.com/cespare/reflex
	github.com/golang/mock/mockgen
	github.com/motemen/go-generate-jsschema/cmd/gojsschemagen
	golang.org/x/lint/golint
)
