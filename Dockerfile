FROM golang:1.26

WORKDIR /app


SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN \
    curl -fsSL https://deb.nodesource.com/setup_26.x | bash - && \
    apt-get install --no-install-recommends -yq nodejs && \
    npm install -g corepack@0.36.0 && \
    corepack enable && \
    apt-get clean && \
    rm -rf /var/cache/apt/archives/* /var/lib/apt/lists/*

COPY go.mod go.sum ./
RUN go mod download

COPY Makefile *.json *.js pnpm-lock.yaml pnpm-workspace.yaml *.go ./
COPY static static
COPY lib lib
COPY cmd cmd
COPY scripts scripts
RUN PUPPETEER_SKIP_DOWNLOAD=true make setup
RUN make build BUILDFLAGS='-mod=readonly'

EXPOSE 8080

# For self-signed GitHub Enterprise Server
CMD if [ -n "$LOCAL_CA_CERT_BASE64" ]; then echo "$LOCAL_CA_CERT_BASE64" | base64 --decode > /usr/local/share/ca-certificates/local.crt; fi && \
    if [ -n "$(ls -1 /usr/local/share/ca-certificates)" ]; then update-ca-certificates; fi && \
    exec ./prchecklist

