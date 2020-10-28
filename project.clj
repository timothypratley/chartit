(defproject timothypratley/chartit "0.1.0-SNAPSHOT"
  :description "Reports metrics on work completed"
  :url "http://github.com/timothypratley/chartit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.9.1"
  :main chartit.exec
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/data.csv "1.0.0"]
                 [fipp "0.6.23"]
                 [floatingpointio/graphql-builder "0.1.14"]
                 [instaparse "1.4.10"]
                 [happygapi "0.4.5"]
                 [meander/epsilon "0.0.512"]
                 [clj-http "3.10.3"]
                 [cheshire "5.10.0"]
                 [clojure.java-time "0.3.2"]
                 [ring "1.8.2"]]

  :source-paths ["src"]
  :profiles {:uberjar {:aot [chartit.exec]}})
