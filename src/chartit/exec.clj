(ns chartit.exec
  (:gen-class)
  (:require [chartit.clubhouse :as clubhouse]
            [chartit.github :as github]
            [chartit.graphql :as graphql]
            [chartit.gsheet :as gsheet]
            [chartit.justworks :as justworks]
            [chartit.local-file :as local-file]
            [chartit.util :as util]
            [clojure.string :as str]
            [again.core :as again]
            [happy.util :as hu]))

;; TODO: plot start-dates
;; TODO: list members

;; Unused
(defn upload-pull-requests [spreadsheet-title pull-requests]
  (let [spreadsheet-id (gsheet/ensure-spreadsheet spreadsheet-title)]
    (gsheet/set-sheet-data spreadsheet-id "pull_requests"
                           (github/pull-requests-as-rows pull-requests))))

(defn upload-github-gsheet [pull-requests]
  (gsheet/create-sheets :all-pull-requests
                        {"all" pull-requests}
                        "pull requests merged"
                        github/pull-requests-as-rows
                        github/bucket-pull-requests-with-tenure)
  (gsheet/create-sheets :pull-requests-by-person
                        (group-by #(-> % :author :login)
                                  pull-requests)
                        "pull requests merged"
                        github/pull-requests-as-rows
                        github/bucket-pull-requests)
  (gsheet/create-sheets :pull-request-reviews-by-person
                        (group-by #(-> % :author :login)
                                  (github/reviews pull-requests))
                        "reviews submitted"
                        github/reviews-as-rows
                        github/bucket-reviews)
  (gsheet/create-sheets :pull-request-review-time-by-person
                        (group-by #(-> % :author :login)
                                  (github/reviews pull-requests))
                        "mean hours to review"
                        github/reviews-as-rows
                        github/bucket-review-times)
  (gsheet/create-sheets :pull-requests-by-group
                        (util/group-by-groups #(-> % :author :login (github/login-groups))
                                              pull-requests)
                        "pull requests merged"
                        github/pull-requests-as-rows
                        github/bucket-pull-requests)
  (gsheet/create-sheets :pull-request-reviews-by-group
                        (util/group-by-groups #(-> % :author :login (github/login-groups))
                                              (github/reviews pull-requests))
                        "reviews submitted"
                        github/reviews-as-rows
                        github/bucket-reviews)
  (gsheet/create-sheets :pull-request-review-time-by-group
                        (util/group-by-groups #(-> % :author :login (github/login-groups))
                                              (github/reviews pull-requests))
                        "mean hours to review"
                        github/reviews-as-rows
                        github/bucket-review-times)
  (gsheet/create-sheets :pull-requests-by-repo
                        (select-keys
                          (group-by #(-> % :repository :name)
                                    pull-requests)
                          (map :name (github/active-repositories)))
                        "pull requests merged"
                        github/pull-requests-as-rows
                        github/bucket-pull-requests)
  (gsheet/create-sheets :pull-request-review-time-by-repo
                        (select-keys
                          (group-by #(-> % :pull-request :repository :name)
                                    (github/reviews pull-requests))
                          (map :name (github/active-repositories)))
                        "mean hours to review"
                        github/reviews-as-rows
                        github/bucket-review-times))

(defn github-gsheet []
  (println "Github: Fetching")
  (let [pull-requests (local-file/read-file "pull_requests")
        last-updated (->> pull-requests (map :updatedAt) (sort) (last))
        new-pull-requests (github/all-pull-requests last-updated)
        pull-requests (concat pull-requests new-pull-requests)
        pull-requests (github/dedupe-updated-pull-requests pull-requests)
        pull-requests (sort-by :mergedAt pull-requests)]
    (println "Github: Fetched" (count new-pull-requests) "new pull requests, now have" (count pull-requests))
    (when (seq new-pull-requests)
      (local-file/save "pull_requests" pull-requests)
      (upload-github-gsheet pull-requests))))

;; TODO: percentiles? % in target range?

(def clubhouse-data-sets
  [["features" clubhouse/*features]
   ["bugs" clubhouse/*bugs]
   ["chores" clubhouse/*chores]])

(def clubhouse-statistics
  [["lead time" clubhouse/story-lead-days]
   ["turnaround time" clubhouse/story-turnaround-days]])

(defn clubhouse-statistics-gsheet [spreadsheet-id]
  (doseq [[data-label stories] clubhouse-data-sets
          [stat-label scalar-fn] clubhouse-statistics
          :let [title (str stat-label " weekly " data-label)
                stats (clubhouse/bucket-stats @stories scalar-fn)]]
    (gsheet/set-sheet-data spreadsheet-id title stats)))

#_(clubhouse/bucket-stats clubhouse/features clubhouse/story-lead-days)

(defn clubhouse-open-closed-gsheet [spreadsheet-id]
  (try
    (let [open-closed (clubhouse/open-vs-closed (clubhouse/remove-archived-incomplete @clubhouse/*all-stories))]
      (gsheet/set-sheet-data spreadsheet-id "all_stories" open-closed))
    (catch Exception ex
      (.printStackTrace ex))))

;; TODO: split by story type group and person
(defn upload-clubhouse-gsheet []
  (let [spreadsheetId (gsheet/ensure-spreadsheet :stories)]
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
  (println "Clubhouse: Fetched" (count @clubhouse/*all-stories) "stories")
  (upload-clubhouse-gsheet))

;; TODO: better user management
(defn users-gsheet []
  (let [spreadsheetId (gsheet/ensure-spreadsheet :users)]
    (gsheet/set-sheet-data spreadsheetId "Justworks"
                           (graphql/nodes2rows (justworks/company-directory)))
    (gsheet/set-sheet-data spreadsheetId "Github"
                           (graphql/nodes2rows (github/users)))))

(comment
  (def pull-requests (local-file/read-file "pull_requests"))
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

(defmacro with-get-response-retries
  [& body]
  `(let [get-response# @#'hu/get-response]
     (with-redefs [hu/get-response (fn get-response-with-retries# [& args#]
                                     (again/with-retries [2000 5000 10000 15000]
                                                         (apply get-response# args#)))]
       ~@body)))

(defn -main [& args]
  (with-get-response-retries
    (gsheet/init!)
    (println "github-gsheet")
    (github-gsheet)
    (println "clubhouse-gsheet")
    (clubhouse-gsheet)
    (println "users-gsheet")
    (users-gsheet))
  :done)

(comment
  (-main))
