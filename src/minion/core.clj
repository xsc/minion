(ns minion.core
  (:require [minion
             [init :refer :all]
             [shortcuts :refer [shortcuts!]]
             [nrepl :as nrepl]
             [system :as system]]
            [clojure.tools.logging :refer [info debug warn error]]
            [clojure.tools.cli :refer [parse-opts]]))

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
     [[nil "--help"]]))

(defn- defonce-maybe
  [sym]
  (if (-> sym meta :not-once)
    `(def ~sym nil)
    `(defonce ~sym nil)))

(defn- create-system-vars
  "Create system/nREPL vars."
  [{:keys [system-as nrepl-as nrepl start stop]}]
  `(do
     ~(defonce-maybe system-as)
     ~(defonce-maybe nrepl-as)
     (nrepl/set-opts! (var ~nrepl-as) ~nrepl)
     (let [system-map# (system/system-map
                         ~system-as
                         ~start
                         ~stop
                         ~nrepl-as)]
       (alter-meta! (var ~system-as) assoc ::system system-map#)
       (->> (fn [v#]
              (if-not (:minion-opts v#)
                (assoc v# :minion-opts [{} []])
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
        (let [opts# (-> (var ~system-as) meta :minion-opts)]
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
          (shutdown! sys# exit-system#))))))

(defn- create-shutdown-hook
  [{:keys [shutdown-as hook?]}]
  (when hook?
    `(do
       (debug "registering shutdown hook ...")
       (doto (Runtime/getRuntime)
         (.addShutdownHook
           (Thread.
             (fn []
               (try
                 (debug "running shutdown hook ...")
                 (~shutdown-as false)
                 (debug "shutdown hook has run.")
                 (catch Throwable t#
                   (warn t# "in shutdown hook."))))))))))

(def ^:dynamic *exit-on-error?*
  true)

(defn- create-main
  "Create main function."
  [{:keys [restart-as shortcuts default-port nrepl?]
    :or {nrepl? true}
    :as opts} sym]
  (let [sym (->> {:arglists '([& args])}
                 (with-meta sym))]
    `(let [shortcuts# (delay (shortcuts! (quote ~shortcuts)))]
       (defn ~sym
         [& args#]
         @shortcuts#
         (if (map? (first args#))
           (let [v# (~restart-as
                      (merge
                        {:repl-port ~default-port
                         :repl? true}
                        (first args#)
                        ~(if-not nrepl?
                           {:repl? false}))
                      (rest args#))]
             (if (= v# :error)
               (throw
                 (IllegalStateException.
                   "error during startup!"))
               ~(create-shutdown-hook opts))
             v#)
           (let [v# (~restart-as args#)]
             (if (and (= v# :error) *exit-on-error?*)
               (System/exit 1)
               ~(create-shutdown-hook opts))
             v#))))))

;; ## Main

(defn- private-sym
  "Create a private symbol."
  []
  (with-meta (gensym) {:private true}))

(defn- ensure-syms
  "Ensure that the given keys contain symbols. Inserts a private
   by default."
  [m ks]
  (reduce
    (fn [m k]
      (update-in m [k]
                 #(if %
                    (with-meta
                      (symbol (name %))
                      (meta %))
                    (private-sym))))
    m ks))

(defn- opts-and-defaults
  "Process options, add defaults."
  [opts]
  (-> (merge
        {:exit?          true
         :hook?          false
         :nrepl?         true
         :system-as      'system
         :nrepl-as       'nrepl
         :restart-as     'restart!
         :shutdown-as    'shutdown!}
        opts)
      (ensure-syms
        [:restart-as
         :shutdown-as
         :system-as
         :nrepl-as])))

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
   - `:nrepl`: options that will be passed to the nREPL server function.
   - `:shortcuts`: a map to be passed to `minion.shortcuts/shortcut!`.
   - `:nrepl-as`: the symbol used to create the nREPL server var (default: `nrepl`).
   - `:system-as`: the symbol used to create the system var (default: `system`).
   - `:restart-as`: the symbol used to create a restart function (default: `restart!`).
   - `:shutdown-as`: the symbol used to create a shutdown function (default: `shutdown!`).
   - `:exit?`: whether or not to exit on shutdown.
   - `:hook?`: whether or not to register a shutdown hook that calls `:stop (default: false).

   Note that you will run into trouble if you explicitly run `System/exit` within the stop fn.
   "
  [sym & {:keys [start stop command-line shortcuts usage
                 default-port nrepl
                 nrepl-as system-as restart-as shutdown-as
                 exit? hook? nrepl?]
          :or {exit?       true
               nrepl?      true
               hook?       false
               system-as   'system
               nrepl-as    'nrepl
               restart-as  'restart!
               shutdown-as 'shutdown!}
          :as opts}]
  (let [opts (opts-and-defaults opts)]
    `(do
       ~(create-system-vars opts)
       ~(create-restart opts)
       ~(create-shutdown opts)
       ~(create-main opts sym))))
