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

To pull down the latest data and upload to sheets:

    lein run


## Development

### Testing

    lein test

### CLJ

See `exec/-main` for the entry point.

### CLJS

Experimental UI features... WIP

To get an interactive development environment run:

    lein figwheel

and open your browser at [localhost:3449](http://localhost:3449/).
This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

    lein clean

To create a production build run:

    lein do clean, cljsbuild once min

And open your browser in `resources/public/index.html`. You will not
get live reloading, nor a REPL. 


## License

Copyright © 2020 Timothy Pratley

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
