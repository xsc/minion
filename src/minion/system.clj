(ns minion.system
  (:require [minion.nrepl :as nrepl]
            [clojure.tools.logging :refer [info warn]]))

;; ## Helpers

(defmacro wrap-info
  "Wrap the given function to print INFO log messages before and after
   execution."
  [before after f]
  `(let [f# ~f]
     (when f#
       (fn [& args#]
         (info ~before)
         (let [r# (apply f# args#)]
           (info ~after)
           r#)))))

(defmacro wrap-nil-warning
  [f]
  `(let [f# ~f]
     (if f#
       (fn [& args#]
         (let [r# (apply f# args#)]
           (if (nil? r#)
             (do
               (warn "startup function returned nil (this might cause problems).")
               ::nil)
             r#)))
       (constantly ::nil))))

(defmacro system-map
  [sym start stop nrepl-sym]
  `(hash-map
     :var (var ~sym)
     :nrepl (var ~nrepl-sym)
     :start (wrap-nil-warning
              (wrap-info
                "starting up system ..."
                "system is running. smoothly."
                ~start))
     :stop (wrap-info
             "shutting down system ..."
             "system shut down."
             ~stop)))

;; ## Startup/Shutdown

(defn restart-system!
  "Restart system stored in the given var."
  [{:keys [var start stop nrepl]} {:keys [repl? repl-port] :as opts} args]
  (when (and repl? repl-port nrepl (not @nrepl))
    (nrepl/start-nrepl! nrepl repl-port))
  (->> (fn [system]
         (when (and system stop)
           (stop system))
         (when start
           (start opts args)))
       (alter-var-root var)))

(defn shutdown-system!
  "Shutdown system/nREPL stored in the given vars."
  [{:keys [var nrepl stop]}]
  (->> (fn [system]
         (when (and system stop)
           (stop system))
         nil)
       (alter-var-root var))
  (when (and nrepl @nrepl)
    (nrepl/stop-nrepl! nrepl)))
