(ns minion.cli-test
  (:require [clojure.test :refer :all]
            [clojure.tools.cli :refer [parse-opts]]
            [minion.cli :as cli]))

(deftest t-opts
  (testing "short/long opt is successfully created."
    (are [opt arg r] (= (@#'cli/make-opts opt arg) r)
         "-k"        nil  ["-k" nil]
         "--k"       nil  [nil "--k"]
         "--k"       "v"  [nil "--k v"]
         :k          nil  ["-k" nil]
         :ks         nil  [nil "--ks"]
         :ks         "v"  [nil "--ks v"]
         [:k]        "v"  ["-k" "--k v"]
         [:k :ks]    nil  ["-k" "--ks"]
         [:k :ks]    "v"  ["-k" "--ks v"]))
  (testing "value argument needs long opt."
    (are [opt] (thrown? AssertionError (@#'cli/make-opts opt "v"))
         :k
         "-k"
         [:k nil])))

(deftest t-flag
  (testing "boolean switches."
    (let [spec (vector
                 (cli/flag [:v :verbose])
                 (cli/disable-flag :no-colors :colors?))]
      (are [args effects] (let [{:keys [options errors]} (parse-opts args spec)
                                ks (keys effects)]
                            (is (empty? errors))
                            (is (= options effects)))
           []             {:verbose false :colors? true}
           ["-v"]          {:verbose true :colors? true}
           ["--verbose"]   {:verbose true :colors? true}
           ["--no-colors"] {:verbose false :colors? false}
           ["--verbose"
            "--no-colors"] {:verbose true :colors? false}))))

(deftest t-string
  (testing "string switches."
    (let [spec (vector
                 (cli/string :host "HOST" :default "localhost")
                 (cli/string :name "NAME"))]
      (are [args effects] (let [{:keys [options errors]} (parse-opts args spec)]
                            (is (empty? errors))
                            (is (= options effects)))
           []             {:host "localhost"}
           ["--name" "n"] {:host "localhost", :name "n"}
           ["--host" "h"] {:host "h"}))))

(deftest t-float
  (testing "float switches."
    (let [spec (vector
                 (cli/float  :pi "FLOAT" :default 3.14)
                 (cli/float  :ratio "RATIO"))]
      (are [args effects] (let [{:keys [options errors]} (parse-opts args spec)]
                            (is (empty? errors))
                            (is (= options effects)))
           []                 {:pi 3.14}
           ["--ratio" "1.12"] {:pi 3.14, :ratio 1.12}
           ["--pi" "3.15"]    {:pi 3.15})
      (are [args] (not (empty? (-> (parse-opts args spec) :errors)))
           ["--pi" "PI"]
           ["--ratio" "NaN"]))))

(deftest t-integer
  (testing "integer switches."
    (let [spec (vector
                 (cli/integer :size  "NUM" :default 1)
                 (cli/positive-integer :count "NUM")
                 (cli/negative-integer :sub "NUM"))]
      (are [args effects] (let [{:keys [options errors]} (parse-opts args spec)]
                            (is (empty? errors))
                            (is (= options effects)))
           []              {:size 1}
           ["--count" "5"] {:size 1, :count 5}
           ["--sub" "-5"] {:size 1, :sub -5}
           ["--size" "2"]  {:size 2})
      (are [args] (not (empty? (-> (parse-opts args spec) :errors)))
           ["--count" "NUM"]
           ["--count" "-1"]
           ["--sub" "1"]
           ["--size" "NaN"]))))

(deftest t-port
  (testing "port number switches."
    (let [spec (vector
                 (cli/port :port 1177))]
      (are [args effects] (let [{:keys [options errors]} (parse-opts args spec)]
                            (is (empty? errors))
                            (is (= options effects)))
           []               {:port 1177}
           ["--port" "12"]  {:port 12})
      (are [args] (not (empty? (-> (parse-opts args spec) :errors)))
           ["--port" "X"]
           ["--port" "-1"]
           ["--port" "70000"]))))
