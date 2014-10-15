(ns minion.core
  (:require [minion
             [nrepl :as nrepl]
             [shortcuts :refer [shortcuts!]]
             [system :as system]]
            [clojure.tools.logging :refer [info debug]]
            [clojure.tools.cli :refer [parse-opts]]
            [potemkin :refer [unify-gensyms]]))

;; ## Initialization

(defn print-help
  "Print help text + usage."
  [{:keys [options summary]} usage]
  (when-let [help (:help options)]
    (when usage
      (println usage)
      (println))
    (println summary)
    (println)
    :help))

(defn print-errors
  "Print error data."
  [{:keys [errors]}]
  (when (seq errors)
    (doseq [e errors]
      (println e))
    :error))

(defn startup!
  [{:keys [var] :as sys} options arguments]
  (system/restart-system! sys options arguments)
  (alter-meta! var assoc ::opts [options arguments])
  :ok)

;; ## Macro Helpers

(defn- prepare-command-line
  "Add nREPL port and help switches to command line args."
  [{:keys [command-line default-port nrepl?]}]
  `(concat
     ~command-line
     ~(when nrepl?
        [[nil "--repl-port PORT" "port for nREPL."
          :id :repl-port
          :default default-port
          :parse-fn #(Integer/parseInt %)]
         [nil "--no-repl" "disable nREPL."
          :id :repl?
          :default true
          :parse-fn not]])
     [["-h" "--help"]]))

(defn- create-system-vars
  "Create system/nREPL vars."
  [{:keys [system-as nrepl-as start stop]}]
  `(do
     (defonce ~system-as nil)
     (defonce ~nrepl-as nil)
     (let [system-map# (system/system-map
                         ~system-as
                         ~start
                         ~stop
                         ~nrepl-as)]
       (alter-meta! (var ~system-as) assoc ::system system-map#)
       (->> (fn [v#]
              (if-not (::opts v#)
                (assoc v# ::opts [{} []])
                v#))
            (alter-meta! (var ~system-as))))))

(defn- create-restart
  "Create startup/restart function."
  [{:keys [restart-as system-as command-line usage]
    :as opts}]
  `(let [opts# ~(prepare-command-line opts)
         usage# ~usage]
     (defn ~restart-as
       ([]
        (let [opts# (-> (var ~system-as) meta ::opts)]
          (assert (= (count opts#) 2))
          (apply ~restart-as opts#)))
       ([args#]
        (let [result# (parse-opts args# opts#)]
          (or (print-errors result#)
              (print-help result# usage#)
              (~restart-as
                (:options result#)
                (:arguments result#)))))
       ([options# arguments#]
        {:pre [(map? options#)
               (sequential? arguments#)]}
        (let [var# (var ~system-as)
              sys# (-> var# meta ::system)]
          (if @var#
            (info "restarting application ...")
            (info "starting up application ..."))
          (startup! sys# options# arguments#))))))

(defn- create-shutdown
  "Create shutdown function."
  [{:keys [shutdown-as system-as exit?]}]
  `(let [exit# ~exit?]
     (defn ~shutdown-as
       ([] (~shutdown-as exit#))
       ([exit-system#]
        (let [sys# (-> (var ~system-as) meta ::system)]
          (system/shutdown-system! sys# exit-system#))))))

(defn- create-main
  "Create main function."
  [{:keys [restart-as shortcuts]} sym]
  (let [sym (->> {:arglists '([& args])}
                 (with-meta sym))]
    `(let [shortcuts# (quote ~shortcuts)]
       (defn ~sym
         [& args#]
         (shortcuts! shortcuts#)
         (~restart-as args#)))))

;; ## Main

(defmacro defmain
  "Define function for application execution. The following options are available:

   - `:start`: a single-arity function that initializes and returns a value representing the
     running application based on a map of options.
   - `:stop`: a single-arity function that processes the value created by `:start` and frees all
     resources associated with the application.
   - `:command-line`: a `tools.cli` compatible vector of command line switches; note that
     `--repl-port` and `-h`/`--help` will be automatically added.
   - `:usage`: a string to be displayed above the option summary when using the `--help` switch,
   - `:default-port`: the default nREPL port. if this is given a nREPL server will always be
     started; otherwise only if the `--repl-port` switch is given.
   - `:shortcuts`: a map to be passed to `minion.core/shortcut!`.
   - `:nrepl-as`: the symbol used to create the nREPL server var (default: `nrepl`).
   - `:system-as`: the symbol used to create the system var (default: `system`).
   - `:restart-as`: the symbol used to create a restart function (default: `restart!`).
   - `:shutdown-as`: the symbol used to create a shutdown function (default: `shutdown!`).
   - `:exit?`: whether or not to exit on shutdown.
   "
  [sym & {:keys [start stop command-line shortcuts default-port
                 nrepl-as system-as restart-as shutdown-as exit?
                 nrepl? usage]
          :or {exit?        true
               nrepl?       true
               system-as    'system
               nrepl-as     'nrepl
               restart-as   'restart!
               shutdown-as  'shutdown!}
          :as opts}]
  (let [opts (merge-with
               (fn [a b]
                 (if (nil? b) a b))
               {:exit?       true
                :nrepl?      true
                :system-as   'system
                :nrepl-as    'nrepl
                :restart-as  'restart!
                :shutdown-as 'shutdown!}
               opts)]
    `(do
       ~(create-system-vars opts)
       ~(create-restart opts)
       ~(create-shutdown opts)
       ~(create-main opts sym))))
