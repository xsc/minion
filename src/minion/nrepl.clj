(ns minion.nrepl
  (:require [clojure.tools.logging :refer [info debug]]
            [clojure.tools.nrepl.server :refer [start-server stop-server]]))

(def ^:private default-options
  {:bind "127.0.0.1"})

(defn set-opts!
  [nrepl-var opts]
  (alter-meta! nrepl-var assoc :opts (into {} opts)))

(defn start-nrepl!
  "Start nREPL and store in the given var."
  [nrepl-var port]
  (let [opts (->> (-> nrepl-var meta :opts)
                  (merge default-options)
                  (apply concat))]
    (alter-var-root nrepl-var
                    #(do
                       (when % (stop-server %))
                       (info "starting up nREPL server on port" port "...")
                       (let [s (apply start-server :port port opts)]
                         (info "nREPL server is running ...")
                         s)))))

(defn stop-nrepl!
  "Stop nREPL stored in the given var."
  [nrepl-var]
  (alter-var-root nrepl-var
                  #(when %
                     (info "shutting down nREPL server ...")
                     (stop-server %)
                     (info "nREPL server shut down."))))
