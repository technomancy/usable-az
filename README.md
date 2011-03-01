# usable-az

* **Q:** How long would it take to write a better search than the one on AgileZen.com?  
* **A:** About an hour.

## Usage

Edit resources/config.clj to add [your API
key](https://agilezen.com/settings#developer) and a map of projects
you want to work with keying name to id.

    $ lein run index safe

    $ lein run search safe comments regression

    $ lein run search safe timeout # field defaults to "all"

To avoid startup time, run <tt>lein interactive</tt> to keep the JVM
resident, and do <tt>search safe timeout</tt> inside the interactive
session:

    $ lein interactive
    Welcome to Leiningen. Type help for a list of commands.
    lein> run search safe regression
    [...]

## License

Copyright (C) 2011 Phil Hagelberg

Distributed under the Eclipse Public License, the same as Clojure.
