# hn-filter

Filter hn results and email them. Currently a very raw work in progress, not
really usable without modifying the source.

## Installation

Requires following environment variables (hardwired to gmail smtp currently):

* HN_EMAIL_USERNAME
* HN_EMAIL_PASSWORD

Should be scheduled to run periodically:

    >crontab -e
    0 9 * * * ~/source/hn-filter/lein run

## TODO

* ...

