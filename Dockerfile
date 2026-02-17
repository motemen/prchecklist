FROM golang:1.26

WORKDIR /app

# Install Node.js 24.x directly from official binary
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN \
    curl -fsSL https://nodejs.org/dist/v24.13.1/node-v24.13.1-linux-x64.tar.gz -o node.tar.gz && \
    tar -xzf node.tar.gz -C /usr/local --strip-components=1 && \
    rm node.tar.gz

# Install Yarn using corepack (included with Node.js 16.10+)
RUN corepack enable && corepack prepare yarn@stable --activate

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

