# chartit

Reports metrics on work completed. Useful for managing work and people.


## Overview

Pulls work descriptions (Github pull requests and Clubhouse stories),
calculates interesting metrics,
and publishes tables with charts to a Google Spreadsheet.


## Setup

Access to data in Github, Clubhouse, and Google requires configuring tokens.
Copy `config-template.edn` to `config.edn` and update `config.edn` with tokens from:

* [Create a Github token](https://help.github.com/en/github/authenticating-to-github/creating-a-personal-access-token-for-the-command-line)
* [Create a Clubhouse token](https://help.clubhouse.io/hc/en-us/articles/205701199-Clubhouse-API-Tokens)
* [Create a Google Oauth2 App](https://support.google.com/googleapi/answer/6158849?hl=en&ref_topic=7013279)


## Running

Requires Java and [Leiningen](https://leiningen.org/) to be installed.

To pull down the latest data and upload to sheets:

    lein run


## Deployment

To create an uberjar:

    lein uberjar

Running on a server requires Java:

    java -jar target/chartit-0.1.0-SNAPSHOT.jar

[Create a Service account](https://developers.google.com/identity/protocols/oauth2/service-account)
and add the private_key and client_email to config.edn.
This is for server to server communication where user interaction is not possible.
The private_key and client_email will be used for authentication instead of user redirect.

## Development

See `exec/-main` for the entry point.

### Testing

    lein test


## License

Copyright © 2020 Timothy Pratley

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
