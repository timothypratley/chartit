(defproject chartit "0.1.0-SNAPSHOT"
  :description "Reports metrics on work completed"
  :url "http://github.com/timothypratley/chartit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.9.1"

  :main chartit.exec

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.597"]
                 [org.clojure/core.async  "0.7.559"]
                 [org.clojure/data.csv "0.1.4"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [org.xerial/sqlite-jdbc "3.30.1"]
                 [reagent "0.9.1"]
                 [fipp "0.6.22"]
                 [floatingpointio/graphql-builder "0.1.8"]
                 [happygapi "0.4.2"]
                 [hickory "0.7.1"]
                 [incanter "1.9.3"]
                 [meander/epsilon "0.0.373"]
                 [clj-http "3.10.0"]
                 [cheshire "5.9.0"]
                 [clojure.java-time "0.3.2"]
                 [ring "1.8.0"]]

  :plugins [[lein-figwheel "0.5.19"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src"]

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src"]
                :figwheel {:on-jsload "chartit.core/on-js-reload"
                           :open-urls ["http://localhost:3449/index.html"]}

                :compiler {:main chartit.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/chartit.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true
                           ;; To console.log CLJS data-structures make sure you enable devtools in Chrome
                           ;; https://github.com/binaryage/cljs-devtools
                           :preloads [devtools.preload]}}
               {:id "min"
                :source-paths ["src"]
                :compiler {:output-to "resources/public/js/compiled/chartit.js"
                           :main chartit.core
                           :optimizations :advanced
                           :pretty-print false}}]}

  :figwheel {:css-dirs ["resources/public/css"]}

  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.11"]
                                  [figwheel-sidecar "0.5.19"]]
                   :source-paths ["src" "dev"]
                   :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                                     :target-path]}})
