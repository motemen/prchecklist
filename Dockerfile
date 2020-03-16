FROM golang:1.14

WORKDIR /app


SHELL ["/bin/bash", "-c"]
# FIXME
RUN \
    set -o pipefail && \
    curl -sS https://dl.yarnpkg.com/debian/pubkey.gpg | apt-key add - && \
    echo "deb https://dl.yarnpkg.com/debian/ stable main" | tee /etc/apt/sources.list.d/yarn.list && \
    apt-get update && \
    apt-get install --no-install-recommends -yq nodejs yarn && \
    apt-get clean && \
    rm -rf /var/cache/apt/archives/* /var/lib/apt/lists/*

ADD go.mod go.sum ./
RUN go mod download

ADD . .
RUN rm -rf node_modules && make BUILDFLAGS='-mod=readonly'

EXPOSE 80
CMD ["./prchecklist"]

