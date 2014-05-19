(ns minion.core
  (:require [clojure.tools.logging :refer [info debug]]
            [clojure.tools.nrepl.server :refer [start-server stop-server]]
            [clojure.tools.cli :refer [parse-opts]]
            [potemkin :refer [unify-gensyms]]))

;; ## Namespace Switching

(defn shortcuts!
  "Create shortcuts for namespace switching. Takes a map of `function -> target-namespace`
   associations and interns the given function into all other target namespaces, as well
   as the `user` one."
  ([m] (shortcuts! m false))
  ([m force?]
   {:pre [(or (not m) (map? m))
          (every? symbol? (keys m))
          (every? symbol? (vals m))]}
   (->> (for [[f target-ns] m
              src-ns (distinct (cons 'user (vals m)))
              :when (not= target-ns src-ns)]
          (try
            (let [nspace (create-ns src-ns)
                  goto-ns! #(in-ns target-ns)
                  r (ns-resolve nspace f)]
              (if (and (not force?) r)
                (printf "WARN: %s would be overwritten. not creating shortcut.%n" r)
                (intern src-ns f goto-ns!)))
            (catch Throwable ex
              (printf "WARN: could not create shortcut '%s' [%s -> %s]: %s%n"
                      f src-ns target-ns (.getMessage ex)))))
        (filter identity)
        (vec))))

;; ## nREPL

(defn start-nrepl!
  "Start nREPL and store in the given var."
  [nrepl-var port]
  (alter-var-root nrepl-var
                  #(do
                     (when % (stop-server %))
                     (info "starting up nREPL server on port" port "...")
                     (let [s (start-server :port port)]
                       (info "nREPL server is running ...")
                       s))))

(defn stop-nrepl!
  "Stop nREPL stored in the given var."
  [nrepl-var]
  (alter-var-root nrepl-var
                  #(when %
                     (info "shutting down nREPL server ...")
                     (stop-server %)
                     (info "nREPL server shut down."))))

;; ## System

(defn restart-system!
  "Restart system stored in the given var."
  [system-var start stop args]
  (->> (fn [system]
         (when (and system stop)
           (info "stopping system ...")
           (stop system))
         (when start
           (info "starting system ...")
           (apply start args)))
       (alter-var-root system-var))
  (info "system is running. smoothly."))

(defn shutdown-system!
  "Shutdown system/nREPL stored in the given vars and exit if desired."
  [system-var nrepl-var stop exit?]
  (->> (fn [system]
         (when (and system stop)
           (info "shutting down system ...")
           (stop system)
           (info "system is shut down.")))
       (alter-var-root system-var))
  (stop-nrepl! nrepl-var)
  (when exit?
    (info "exitting application ...")
    (System/exit 0)))

;; ## Main

(defn- prepare-command-line
  "Add nREPL port and help switches to command line args."
  [{:keys [command-line default-port]}]
  `(concat
     ~command-line
     [[nil "--repl-port PORT" "port for nREPL"
       :id :repl-port
       :default ~default-port
       :parse-fn #(Integer/parseInt %)]
      ["-h" "--help"]]))

(defmacro defmain
  "Define function for application execution. The following options are available:

   - `:start`: a single-arity function that initializes and returns a value representing the
     running application based on a map of options.
   - `:stop`: a single-arity function that processes the value created by `:start` and frees all
     resources associated with the application.
   - `:command-line`: a `tools.cli` compatible vector of command line switches; note that
     `--repl-port` and `-h`/`--help` will be automatically added.
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
                 nrepl-as system-as restart-as shutdown-as exit?]
          :or {exit?        true
               system-as    'system
               nrepl-as     'nrepl
               restart-as   'restart!
               shutdown-as  'shutdown!}
          :as opts}]
  (let [nrepl (or nrepl-as (with-meta (gensym "nrepl") {:private true}))
        system (or system-as (with-meta (gensym "system") {:private true}))]
    (unify-gensyms
      `(let [opts# ~(prepare-command-line opts)
             start## ~start
             stop## ~stop
             shortcuts# (quote ~shortcuts)
             exit## ~exit?
             cli-opts## (atom nil)]

         (defonce ~nrepl nil)
         (defonce ~system nil)

         ~(when restart-as
            `(defn ~restart-as
               []
               (restart-system! (var ~system) start## stop## @cli-opts##)))

         ~(when shutdown-as
            `(defn ~shutdown-as
               ([] (shutdown-system! (var ~system) (var ~nrepl) stop## exit##))
               ([e#] (shutdown-system! (var ~system) (var ~nrepl) stop## e#))))

         (defn ~sym
           [& args#]
           (let [result# (parse-opts args# opts#)
                 [options# arguments# errors# summary#] ((juxt :options :arguments :errors :summary) result#)]
             (cond (seq errors#) (do
                                   (doseq [e# errors#]
                                     (println e#))
                                   (System/exit 1))
                   (:help options#) (println summary#)
                   :else (do
                           (reset! cli-opts## [options# arguments#])
                           (info "starting up application ...")
                           (shortcuts! shortcuts#)
                           (when-let [port# (:repl-port options#)]
                             (start-nrepl! (var ~nrepl) port#))
                           (restart-system! (var ~system) start## stop## @cli-opts##)))))))))
