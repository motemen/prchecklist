FROM golang:1.26

WORKDIR /app

# Install Node.js 24.x and Yarn using NodeSource and official Yarn repository
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN \
    curl -fsSL https://deb.nodesource.com/setup_24.x | bash - && \
    curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - && \
    echo "deb https://dl.yarnpkg.com/debian/ stable main" | tee /etc/apt/sources.list.d/yarn.list && \
    apt-get update && \
    apt-get install --no-install-recommends -yq nodejs yarn && \
    apt-get clean && \
    rm -rf /var/cache/apt/archives/* /var/lib/apt/lists/*

COPY go.mod go.sum ./
RUN go mod download

COPY Makefile *.json *.js yarn.lock *.go ./
COPY static static
COPY lib lib
COPY cmd cmd
COPY scripts scripts
RUN make setup
RUN make build BUILDFLAGS='-mod=readonly'

EXPOSE 8080

# For self-signed GitHub Enterprise Server
CMD if [ -n "$LOCAL_CA_CERT_BASE64" ]; then echo "$LOCAL_CA_CERT_BASE64" | base64 --decode > /usr/local/share/ca-certificates/local.crt; fi && \
    if [ -n "$(ls -1 /usr/local/share/ca-certificates)" ]; then update-ca-certificates; fi && \
    exec ./prchecklist

