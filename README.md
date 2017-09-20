# prchecklist

WIP - Checklist based on pull requests

## Development

Requires [Go][] and [yarn][].

Register an OAuth application [on GitHub](https://github.com/settings/applications/new), with callback URL configured as `http://localhost:8080/auth/callback`. Set OAuth client ID/secret as `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` environment variables respectively.

    $ make develop
    $ open http://localhost:8080/

## Building

    $ make # builds "prchecklist" stand-alone binary

## Releases

Release binaries are on [Releases page](https://github.com/motemen/prchecklist/releases). They are uploaded by [Travis CI](https://travis-ci.org/motemen/prchecklist).

[Go]: https://golang.org/
[yarn]: https://yarnpkg.com/
