(ns minion.nrepl
  (:require [clojure.tools.logging :refer [info debug]]
            [clojure.tools.nrepl.server :refer [start-server stop-server]]))

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
