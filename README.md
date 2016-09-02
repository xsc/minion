# minion

__minion__ helps starting up an application by:

- parsing the command line arguments,
- starting an nREPL server,
- handling the main system,
- offering restart/shutdown functions,
- creating shortcut functions to quickly switch between namespaces.

Doesn't seem much but reduces the amount of boilerplate necessary to create a `-main` function.

[![Build Status](https://travis-ci.org/xsc/minion.svg?branch=master)](https://travis-ci.org/xsc/minion)
[![endorse](https://api.coderwall.com/xsc/endorsecount.png)](https://coderwall.com/xsc)

## Usage

__Leiningen__ ([via Clojars](https://clojars.org/minion))

[![Clojars Project](http://clojars.org/minion/latest-version.svg)](http://clojars.org/minion)

__REPL__

```clojure
(require '[minion.core :refer [defmain]])

(defmain -main
  :default-port 1234
  :usage        "Usage: app [<options>] <first name> <last name>"
  :start        (fn [options [first-name last-name]] (generate-and-start-system ...))
  :stop         (fn [system] (destroy-system ...))
  :shortcuts    {cli      my.app.cli
                 main-cli my.app.main}
  :command-line [["-p" "--port" "the system port"
                  :default 9999]])
```

See the docstring of `defmain` for more options.

## Command Line Parsing

The `:command-line` option can contain a vector compatible with
[tools.cli](https://github.com/clojure/tools.cli)'s `parse-opts`. The command
line will be parsed according to this specification, enriched by the switches
`--help` (to display the CLI summary), `--repl-port` (to overwrite or set the
port for the embedded nREPL server) and `--no-repl` (to disable the embedded
nREPL server).

`:usage` can be used to prepend a string to the help output.

```
$ lein run -- --help
Usage: app [<options>] <first name> <last name>

   -p, --port PORT        9999   port for HTTP interface
       --repl-port PORT  port for nREPL.
       --no-repl         disable nREPL.
       --help

```

Options and extra arguments will be passed to the function set as `:start`. In
`minion.cli` you can find some helper functions to reduce the amount of code to
write for the command line spec:

```clojure
(require '[minion.cli :as cli])

(vector
  (cli/flag   :debug            "activate debugging.")
  (cli/string [:f :cfg] "FILE"  "config file.")
  (cli/float  [:r]      "RATIO" "aspect ratio.")])
;; => [[nil "--debug" "activate debugging." :default false]
;;     ["-f" "--cfg FILE" "config file."]
;;     ["-r" "--r RATIO" "aspect ratio."
;;      :parse-fn #<cli$string__GT_double minion.cli$string__GT_double@58bca166>]]
```

## Embedded nREPL

If the `:default-port` option is given, an nREPL server will be started on that
port, even if no `--repl-port` command line switch is given. Otherwise, the
desired port will be used.

You can set the var that stores the server handle using the option `:nrepl-as`
(default: `nrepl`). Additionally, you can customize the startup command using
the `:nrepl` option, e.g. to include
[cider-nrepl](https://github.com/clojure-emacs/cider-nrepl):

```clojure
(defmain -main
  ...
  :nrepl {:handler cider.nrepl/cider-nrepl-handler}
  ...)
```

__NOTE__ that the nREPL server binds to `127.0.0.1` by default but you can
override this behaviour by supplying `:bind` in the `:nrepl` map.

## Namespace Shortcuts

Say you have a namespace `my.app.cli` for managing your application that should
be usable via a remote nREPL connection. But, when you connect you'll end up in
the `user` namespace, having to issue `(in-ns 'my.app.cli)` which is neither
quickly typed nor easily remembered.

The `:shortcut` option can be used to create functions for namespace switching.
They will be accessible from the `user` namespace as well as all those
namespaces that have shortcuts pointing at them. For example, the above usage
example will have created `user/cli` and `my.app.main/cli`, as well as
`user/main-cli` and `my.app.cli/main-cli`.

## System Lifecycle

The function stored in `:start` will be called to create whatever value is
representing your system. This value will be stored in a var whose name is
determined via the option `:system-as` (default: `system`). Now, you have the
following functions to manage your system's lifecycle:

- `(restart!)` will call the function declared as `:stop` on your system before
  instantiating a new one via `:start`; initial options and arguments are
preserved,
- `(shutdown!)` will stop your system _as well as the nREPL server_.

Note that you can set symbols for shutdown and restart using `:shutdown-as` and
`:restart-as`.

Additionally, the function created by `defmain` can be called with a map as its
first parameter, in which case it will be directly passed to the restart
function.

## Shutdown Hook

The stop function can be automatically registered as a shutdown hook if the
`:hook?` option is set to `true`.

## License

Copyright &copy; 2014-2015 Yannick Scherer

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
