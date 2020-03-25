(ns chartit.core
    (:require [reagent.core :as reagent :refer [atom]]))

;; get a schema
;; build paths through navigation
;; aggregate helpers
;; ask for a table/chart
;; show it

;; work with datomic and sql


(def some-data
  (reagent/atom
   [["Week" "Stories"]
    [201935 8]
    [201934 5]
    [201933 1]
    [201930 3]
    [201929 7]
    [201928 3]
    [201927 5]
    [201926 3]
    [201925 5]
    [201924 3]
    [201923 1]]))

(defonce ready?
  (reagent/atom false))

(defonce initialize
  (do
    (js/google.charts.load #js {:packages #js ["corechart"]})
    (js/google.charts.setOnLoadCallback
     (fn google-visualization-loaded []
       (reset! ready? true)))))

(defn data-table [data]
  (cond
    (map? data) (js/google.visualization.DataTable. (clj->js data))
    (string? data) (js/google.visualization.Query. data)
    (seqable? data) (js/google.visualization.arrayToDataTable (clj->js data))))

(defn draw-chart [chart-type data options]
  [:div
   (if @ready?
     [:div
      {:ref
       (fn [this]
         (when this
           (.draw (new (aget js/google.visualization chart-type) this)
                  (data-table data)
                  (clj->js options))))}]
     [:div "Loading..."])])

(defn hello-world []
  [:div
   [:h1 "Google Chart Example"]
   [draw-chart
    "ColumnChart"
    @some-data
    {:title "Title"
     :subtitle "Subtitle"}]])

(reagent/render-component
 [hello-world]
 (. js/document (getElementById "app")))

(defn on-js-reload [])
