# minion

__minion__ helps starting up an application by:

- parsing the command line arguments,
- starting an nREPL server,
- handling the main system,
- offering restart/shutdown functions,
- creating shortcut functions to quickly switch between namespaces.

Doesn't seem much but reduces the amount of boilerplate necessary to create a `-main` function.

[![endorse](https://api.coderwall.com/xsc/endorsecount.png)](https://coderwall.com/xsc)

## Usage

__Leiningen__ ([via Clojars](https://clojars.org/minion))

```clojure
[minion "0.1.2"]
```

__REPL__

```clojure
(require '[minion.core :refer [defmain]])

(defmain -main
  :default-port 1234
  :start        (fn [options args] (generate-and-start-system ...))
  :stop         (fn [system] (destroy-system ...))
  :shortcuts    {cli      my.app.cli
                 main-cli my.app.main}
  :command-line [["-p" "--port" "the system port"
                  :default ...]
                 ...])
```

This will create a `-main` function that:

- parses the command line based on the `:command-line` spec (with additional switches `--repl-port` and `-h/--help`),
- starts an nREPL server on port 1234 (or the one given on the command line),
- starts the system using the function stored in `:start`,
- shuts the system down using the function stored in `:stop`,
- creates a funciton `cli` in namespaces `user` and `my.app.main` that switches to `my.app.cli`,
- creates a function `main-cli` in namespaces `user` and `my.app.cli` that switches to `my.app.main`.

The last two points have a very specific use case. If your application offers some kind of management console
in the namespace `my.app.cli` it is easier to type `(cli)` after connecting to the nREPL server than
`(in-ns 'my.app.cli)`. Additionally, this makes switching between such namespaces seamless.

There are two more (zero-arity) functions that will be created alongside `-main`:

- `restart!` to stop and start the system,
- `shutdown!` to stop the system and nREPL server, then exit the application.

See the docstring of `defmain` for more options.

## License

Copyright &copy; 2014 Yannick Scherer

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
