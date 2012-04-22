(defproject eventual "1.0.0-SNAPSHOT"
  :description "Abstraction of eventual values"
  :url "http://github.com/Gozala/eventual-cljs/"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]]
  :plugins [[lein-cljsbuild "0.1.8"]]
  :cljsbuild
  {:repl-listen-port 9000
   :repl-launch-commands
   {"repl" ["open" "http://localhost:9000/repl-demo"]}
   :builds [{:source-path "src"
             :compiler {:output-to "lib/core.js"
                        :optimizations :whitespace
                        :pretty-print true }}]})