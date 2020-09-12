(defproject chartit "0.1.0-SNAPSHOT"
  :description "Reports metrics on work completed"
  :url "http://github.com/timothypratley/chartit"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.9.1"

  :main chartit.exec

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.773"]
                 [org.clojure/core.async  "1.3.610"]
                 [org.clojure/data.csv "1.0.0"]
                 [org.clojure/java.jdbc "0.7.11"]
                 [org.xerial/sqlite-jdbc "3.32.3.2"]
                 [reagent "0.10.0"]
                 [fipp "0.6.23"]
                 [floatingpointio/graphql-builder "0.1.13"]
                 [instaparse "1.4.10"]
                 [happygapi "0.4.2"]
                 [hickory "0.7.1"]
                 [incanter "1.9.3"]
                 [meander/epsilon "0.0.488"]
                 [clj-http "3.10.2"]
                 [cheshire "5.10.0"]
                 [clojure.java-time "0.3.2"]
                 [ring "1.8.1"]]

  :plugins [[lein-figwheel "0.5.20"]
            [lein-cljsbuild "1.1.8" :exclusions [[org.clojure/clojure]]]]

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

  :profiles {:dev {:dependencies [[binaryage/devtools "1.0.2"]
                                  [figwheel-sidecar "0.5.20"]]
                   :source-paths ["src" "dev"]
                   :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                                     :target-path]}})
