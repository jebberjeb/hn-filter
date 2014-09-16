# hn-filter

Filter hn results and email them. Currently a very raw work in progress, not
really usable without modifying the source.

## Installation

Requires following environment variables (hardwired to gmail smtp currently):

* HN_EMAIL_USERNAME
* HN_EMAIL_PASSWORD

Should be scheduled to run periodically:

    >crontab -e
    
    # Daily HN email
    0 9 * * * ~/source/hn-filter/lein run email
    
    # Dump it periodically (serve it up however)
    */5 * * * * ~/source/hn-filter/lein run >> hn.html

## TODO

* Improve filtering, replace simple text matching
* Replace clojstache w/ hiccup? Maybe use Enlive (already a dependency)

