(ns minion.init
  (:require [minion
             [system :as system]]
            [clojure.tools.logging :refer [error]]))

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
  "Perform startup."
  [{:keys [var] :as sys} options arguments]
  (try
    (do
      (system/restart-system! sys options arguments)
      (alter-meta! var assoc :minion-opts [options arguments])
      :ok)
    (catch Throwable ex
      (error ex "during startup/restart.")
      :error)))

(defn shutdown!
  [sys]
  (try
    (do
      (system/shutdown-system! sys)
      :ok)
    (catch Throwable ex
      (error ex "during shutdown.")
      :error)))
