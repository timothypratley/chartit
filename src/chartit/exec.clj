(ns chartit.exec
  (:require [chartit.clubhouse :as clubhouse]
            [chartit.github :as github]
            [chartit.gsheet :as gsheet]
            [chartit.justworks :as justworks]
            [chartit.util :as util]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [fipp.edn :as fipp]
            [chartit.graphql :as graphql]))

;; TODO: start-date
;; TODO: plot start-dates
;; TODO: list members

(def write-backend #{"iwillig" "rads"})
(def write-frontend #{"jaunpasolano" "christinecha"})
(def enhance-backend #{"opoku" "semperos" "jeremyheiler"})
(def enhance-frontend #{"maryjenel" "charpeni" "bwittenberg"})
(def acquire-backend #{"tobias" "Scriptor" "enaeher" "favila"})
(def acquire-frontend #{"rinchen" "teimurjan"})
(def platform #{"crohacz" "pgroudas"})
(def backend (set/union acquire-backend enhance-backend write-backend))
(def frontend (set/union acquire-frontend enhance-frontend write-frontend))

(def github-login-groups
  {"engineering" (set/union backend frontend platform)
   "backend" backend
   "frontend" frontend
   "acquire" (set/union acquire-backend acquire-frontend)
   "enhance" (set/union enhance-backend enhance-frontend)
   "write" (set/union write-backend write-frontend)
   "platform" platform})

(defn upload-pull-requests [spreadsheet-id pull-requests]
  (gsheet/set-sheet-data spreadsheet-id "pull_requests"
                         (github/pull-requests-as-rows pull-requests)))

;; TODO: divide by group size
(defn bucket-prs [pull-requests]
  (util/buckets2rows
   (util/bucket-by :mergedAt count pull-requests)))

(defn upload-sheet-and-chart [spreadsheet-id title pull-requests]
  (gsheet/set-sheet-data spreadsheet-id title
                   (github/pull-requests-as-rows pull-requests))
  (let [{{:keys [sheetId]} :properties, [{:keys [chartId]}] :charts}
        (gsheet/ensure-sheet spreadsheet-id (str title "_weekly"))]
    (gsheet/set-data spreadsheet-id (str title "_weekly")
                     (cons ["week" "count" "4 week average" "quarterly average" "6 month average"]
                           (-> (bucket-prs pull-requests)
                               (util/with-rolling 4)
                               (util/with-rolling 13)
                               (util/with-rolling 26))))
    (gsheet/create-velocity-chart spreadsheet-id sheetId chartId title (inc (count pull-requests)))))

(defn create-sheet-per-group [spreadsheet-id pull-requests]
  (doseq [[group logins] github-login-groups]
    (let [pull-requests (filter #(-> % :author :login logins) pull-requests)]
      (upload-sheet-and-chart spreadsheet-id group pull-requests))))

(defn create-sheet-per-login [spreadsheet-id pull-requests]
  (let [pull-requests-by-login (group-by #(-> % :author :login) pull-requests)]
    (doseq [[login pull-requests] (sort-by #(str/capitalize (key %)) pull-requests-by-login)]
      (upload-sheet-and-chart spreadsheet-id login pull-requests))))

(defn upload-github-gsheet [pull-requests]
  (let [spreadsheetId (gsheet/ensure-spreadsheet "Clubhouse git charts")]
    (upload-pull-requests spreadsheetId pull-requests)
    (create-sheet-per-group spreadsheetId pull-requests)
    (create-sheet-per-login spreadsheetId pull-requests)))

(defn github-gsheet []
  (println "Github: Fetching")
  ;; TODO: decomplect data storage
  (.mkdirs (io/file "data"))
  (let [data-file (io/file "data" "pull_requests.edn")
        pull-requests (when (.exists data-file)
                        (edn/read-string (slurp data-file)))
        last-updated (last (sort (map :updatedAt pull-requests)))
        new-pull-requests (github/all-pull-requests last-updated)
        pull-requests (remove github/pull-request-by-bot? (concat pull-requests new-pull-requests))
        pull-requests (github/dedupe-updated-pull-requests pull-requests)
        pull-requests (sort-by :mergedAt pull-requests)]
    (println "Github: Fetched" (count new-pull-requests) "new pull requests, now have" (count pull-requests))
    (when (seq new-pull-requests)
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
                           (graphql/nodes2rows (github/users "clubhouse")))))

(comment
 (def g (github/users "clubhouse"))
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
