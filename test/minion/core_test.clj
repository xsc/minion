(ns minion.core-test
  (:require [clojure.test :refer :all]
            [clojure.tools.nrepl :as repl]
            [minion.core :refer [defmain]]))

;; ## Helpers

(defn reset-all
  [system flow-atom]
  (when-let [v (resolve system)]
    (alter-var-root v (constantly nil)))
  (reset! flow-atom [])
  true)

;; ## Tests

(deftest t-main
  (let [flow (atom [])
        f @(defmain m-main
             :exit? false
             :usage "usage-info"
             :command-line [["-o" "--option V" "an option."
                             :default "default"]]
             :start (fn [& args]
                      (swap! flow conj [:start args])
                      :on)
             :stop (fn [sys]
                     (swap! flow conj [:stop sys])))
        reset #(reset-all 'system flow)]
    (is (fn? f))
    (testing "startup/shutdown options"
      (are [args option-value repl-value arguments]
           (do
             (reset)
             (is (= :ok (apply f args)))
             (is (= @#'system :on))
             (is (= (count @flow) 1))
             (is (= (ffirst @flow) :start))
             (let [v (-> @flow first second)
                   {:keys [option repl? repl-port]} (first v)
                   args' (second v)]
               (is (= option option-value))
               (is (= repl-value repl?))
               (is (= args' arguments))
               (is (nil? repl-port)))
             (is (= :ok (shutdown!)))
             (is (nil? @#'system))
             (is (= (count @flow) 2))
             (is (= (last @flow) [:stop :on])))
           []                   "default" true  []
           ["-o" "value"]       "value"   true  []
           ["--option" "value"] "value"   true  []
           ["hey"]              "default" true  ["hey"]
           ["-o" "value" "hey"] "value"   true  ["hey"]
           ["--no-repl"]        "default" false []))))

(deftest t-help
  (let [f @(defmain m-help
             :system-as   help-system
             :restart-as  help-restart
             :shutdown-as help-shutdown
             :exit? false
             :usage "usage-info"
             :command-line [["-o" "--option V"]])]
    (testing "help"
      (let [s (with-out-str
                (is (= :help (f "--help"))))]
        (is (not (empty? s)))
        (are [s'] (.contains ^String s s')
             "usage-info"
             "-o, --option V"
             "--help"
             "--no-repl"
             "--repl-port PORT"))
      (is (= :ok (help-shutdown))))))

(deftest t-restart
  (let [flow (atom [])
        f @(defmain m-restart
             :system-as   restart-system
             :restart-as  restart'
             :shutdown-as restart-shutdown
             :exit? false
             :usage "usage-info"
             :command-line [["-o" "--option V" "an option."
                             :default "default"]]
             :start (fn [& args]
                      (swap! flow conj [:start args])
                      :on)
             :stop (fn [sys]
                     (swap! flow conj [:stop sys])))
        reset #(reset-all 'restart-system flow)]
    (testing "normal restart"
      (reset)
      (is (= :ok (f "-o" "initial-value")))
      (is (= @#'restart-system :on))
      (is (= :ok (restart')))
      (is (= (count @flow) 3))
      (is (= (map first @flow) [:start :stop :start]))
      (let [[opts _ opts'] (map second @flow)]
        (is (= (-> opts first :option) "initial-value"))
        (is (= (-> opts' first :option) "initial-value")))
      (is (= :ok (restart-shutdown))))
    (testing "restart with new arguments"
      (reset)
      (is (= :ok (f)))
      (is (= :ok (restart' ["-o" "value"])))
      (is (= (map first @flow) [:start :stop :start]))
      (let [[opts _ opts'] (map second @flow)]
        (is (= (-> opts first :option) "default"))
        (is (= (-> opts' first :option) "value")))
      (is (= :ok (restart-shutdown))))
    (testing "restart with explicit option map "
      (reset)
      (is (= :ok (f)))
      (is (= :ok (restart' {:option "others"} ["hey"])))
      (is (= (map first @flow) [:start :stop :start]))
      (let [[v _ v'] (map second @flow)]
        (is (= (-> v first :option) "default"))
        (is (= (last v) []))
        (is (= v' [{:option "others"} ["hey"]])))
      (is (= :ok (restart-shutdown))))))

(deftest t-errors
  (binding [minion.core/*exit-on-error?* false]
    (let [f @(defmain m-error
               :system-as   error-system
               :nrepl-as    error-nrepl
               :restart-as  error-restart
               :shutdown-as error-shutdown
               :exit? false
               :start (fn [_ [v]]
                        (if (= v "throw")
                          (throw (Exception. "start"))
                          :on))
               :stop (fn [_]
                       (throw (Exception. "stop"))))]
      (testing "unknown command line option."
        (is (= :error (f "--unknown-option"))))
      (testing "startup exceptions."
        (is (= :error (f "throw"))))
      (testing "shutdown exceptions."
        (is (= :ok (f)))
        (is (= :error (error-shutdown)))))))

(deftest t-nrepl
  (let [port 12343
        connect (fn [p]
                  (try
                    (let [rs (with-open [conn (repl/connect :port p)]
                               (-> (repl/client conn 1000)
                                   (repl/message
                                     {:op "eval"
                                      :code "(+ 3 5)"})
                                   (repl/response-values)))]
                      (if (= (first rs) 8)
                        :repl-ok
                        :repl-failed))
                    (catch java.net.ConnectException ex
                      :repl-error)))
        f @(defmain m-nrepl
             :system-as   nrepl-system
             :restart-as  nrepl-restart
             :shutdown-as nrepl-shutdown
             :nrepl-as    nrepl'
             :exit? false
             :default-port port)]
    (testing "default nREPL"
      (is (= :ok (f)))
      (is (= :repl-ok (connect port)))
      (is (= :ok (nrepl-shutdown)))
      (is (= :repl-error (connect port))))
    (testing "disabled nREPL"
      (is (= :ok (f "--no-repl")))
      (is (= :repl-error (connect port)))
      (is (= :ok (nrepl-shutdown))))
    (testing "command line nREPL port"
      (is (= :ok (f "--repl-port" "12344")))
      (is (= :repl-ok (connect 12344)))
      (is (= :ok (nrepl-shutdown)))
      (is (= :repl-error (connect port))))))
