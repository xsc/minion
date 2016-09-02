(defproject minion "0.4.1"
  :description "Launcher Utilities"
  :url "https://github.com/xsc/minion"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.nrepl "0.2.6"]]
  :profiles {:dev {:dependencies [[com.taoensso/timbre "4.7.4"]]
                   :injections [(require 'clojure.tools.logging 'taoensso.timbre.tools.logging)
                                (taoensso.timbre.tools.logging/use-timbre)
                                (taoensso.timbre/set-level! :fatal)]}}
  :pedantic? :abort)
