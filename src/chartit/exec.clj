(ns chartit.exec
  (:require [chartit.clubhouse :as clubhouse]
            [chartit.github :as github]
            [chartit.graphql :as graphql]
            [chartit.gsheet :as gsheet]
            [chartit.justworks :as justworks]
            [chartit.util :as util]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [fipp.edn :as fipp]
            [chartit.config :as config]))

;; TODO: start-date
;; TODO: plot start-dates
;; TODO: list members

(def github-login-groups
  (config/get-config [:github-login-groups]))

(defn upload-pull-requests [spreadsheet-title pull-requests]
  (let [spreadsheet-id (gsheet/ensure-spreadsheet spreadsheet-title)]
    (gsheet/set-sheet-data spreadsheet-id "pull_requests"
                           (github/pull-requests-as-rows pull-requests))))

(defn upload-sheet-and-chart [spreadsheet-id title metric rows buckets]
  (gsheet/set-sheet-data spreadsheet-id title rows)
  (let [{{:keys [sheetId]} :properties, [{:keys [chartId]}] :charts}
        (gsheet/ensure-sheet spreadsheet-id (str title "_weekly"))]
    (gsheet/set-data spreadsheet-id (str title "_weekly")
                     (cons ["week" metric "4 week average" "quarterly average" "6 month average"]
                           (-> buckets
                               (util/with-rolling 4)
                               (util/with-rolling 13)
                               (util/with-rolling 26))))
    (gsheet/create-velocity-chart spreadsheet-id sheetId chartId title (count rows))))

(defn create-sheets [spreadsheet-title m metric row-fn bucket-fn]
  (let [spreadsheet-id (gsheet/ensure-spreadsheet spreadsheet-title)]
    (doseq [[k vs] (sort-by #(str/capitalize (key %)) m)]
      (upload-sheet-and-chart spreadsheet-id
                              k
                              metric
                              (row-fn vs)
                              (bucket-fn vs)))))

(defn upload-github-gsheet [pull-requests]
  #_(upload-pull-requests "Clubhouse all pull requests" pull-requests)
  #_(create-sheets "Clubhouse pull requests by person"
                 (group-by #(-> % :author :login)
                           pull-requests)
                 github/pull-requests-as-rows
                 github/bucket-pull-requests)
  #_(create-sheets "Clubhouse pull request reviews by person"
                 (group-by #(-> % :author :login)
                           (github/reviews pull-requests))
                 github/reviews-as-rows
                 github/bucket-reviews)
  (create-sheets "Clubhouse pull request review time by person"
                 (group-by #(-> % :author :login)
                           (github/reviews pull-requests))
                 "mean hours to review"
                 github/reviews-as-rows
                 github/bucket-review-times)
  #_(create-sheets "Clubhouse pull requests by group"
                 (util/group-by-groups #(-> % :author :login github-login-groups)
                                       pull-requests)
                 github/pull-requests-as-rows
                 github/bucket-pull-requests)
  #_(create-sheets "Clubhouse pull request reviews by group"
                 (util/group-by-groups #(-> % :author :login github-login-groups)
                                       (github/reviews pull-requests))
                 github/reviews-as-rows
                 github/bucket-reviews)
  (create-sheets "Clubhouse pull request review time by group"
                 (util/group-by-groups #(-> % :author :login github-login-groups)
                                       (github/reviews pull-requests))
                 "mean hours to review"
                 github/reviews-as-rows
                 github/bucket-review-times))

(defn github-gsheet []
  (println "Github: Fetching")
  ;; TODO: decomplect data storage
  (.mkdirs (io/file "data"))
  (let [data-file (io/file "data" "pull_requests.edn")
        pull-requests (when (.exists data-file)
                        (edn/read-string (slurp data-file)))
        last-updated (last (sort (map :updatedAt pull-requests)))
        new-pull-requests (github/all-pull-requests last-updated)
        pull-requests (concat pull-requests new-pull-requests)
        pull-requests (github/dedupe-updated-pull-requests pull-requests)
        pull-requests (sort-by :mergedAt pull-requests)]
    (println "Github: Fetched" (count new-pull-requests) "new pull requests, now have" (count pull-requests))
    (when true ;;(seq new-pull-requests)
      (spit data-file (with-out-str (fipp/pprint pull-requests)))
      (upload-github-gsheet pull-requests))))

;; TODO: percentiles? % in target range?

(def clubhouse-data-sets
  [["features" clubhouse/features]
   ["bugs" clubhouse/bugs]
   ["chores" clubhouse/chores]])

(def clubhouse-statistics
  [["lead time" clubhouse/story-lead-days]
   ["turnaround time" clubhouse/story-turnaround-days]])

(defn clubhouse-statistics-gsheet [spreadsheet-id]
  (doseq [[data-label stories] clubhouse-data-sets
          [stat-label scalar-fn] clubhouse-statistics
          :let [title (str stat-label " weekly " data-label)]]
    (gsheet/set-sheet-data spreadsheet-id title
                           (clubhouse/bucket-stats stories scalar-fn))))

(defn clubhouse-open-closed-gsheet [spreadsheet-id]
  (let [open-closed (clubhouse/open-vs-closed (clubhouse/remove-archived-incomplete clubhouse/all-stories))]
    (gsheet/set-sheet-data spreadsheet-id "all_stories" open-closed)))

;; TODO: split by story type group and person
(defn upload-clubhouse-gsheet []
  (let [spreadsheetId (gsheet/ensure-spreadsheet "Clubhouse stories")]
    (gsheet/set-sheet-data spreadsheetId "stories_completed"
                           (clubhouse/all-completed-stories))
    (gsheet/set-sheet-data spreadsheetId "stories_requested"
                           (clubhouse/all-requested-stories))
    (println "clubhouse-statistics-gsheet")
    (clubhouse-statistics-gsheet spreadsheetId)
    (println "clubhouse-open-closed-gsheet")
    (clubhouse-open-closed-gsheet spreadsheetId)))

(defn clubhouse-gsheet []
  (println "Clubhouse: Fetching")
  (clubhouse/fetch-all)
  (println "Clubhouse: Fetched" (count clubhouse/all-stories) "stories")
  (upload-clubhouse-gsheet))

;; TODO: better user management
(defn users-gsheet []
  (let [spreadsheetId (gsheet/ensure-spreadsheet "Clubhouse users")]
    (gsheet/set-sheet-data spreadsheetId "Justworks"
                           (graphql/nodes2rows (justworks/company-directory)))
    (gsheet/set-sheet-data spreadsheetId "Github"
                           (graphql/nodes2rows (github/users)))))

(comment
 (def g (github/users))
 (def j (justworks/company-directory))
 (def g2 (util/index-by :name g))
 (defn fname [s]
   (str/join " " (reverse (str/split s #", "))))
 (for [{:keys [name] :as e} j]
   (merge (get g2 (fname name)) e))
 (def j2 (util/index-by (comp fname :name) j))
 (count
  (for [{:keys [name] :as gg} g
        :when (not (j2 name))]
    gg)))


;; TODO: KPI/summary

(defn -main [& args]
  (println "github-gsheet")
  (github-gsheet)
  (println "clubhouse-gsheet")
  (clubhouse-gsheet)
  (println "users-gsheet")
  (users-gsheet)
  :done)

(comment
  (-main))
