(ns chartit.clubhouse
  (:require [chartit.config :as config]
            [chartit.util :as util]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [java-time :as t]
            [meander.strategy.epsilon :as s]
            [meander.epsilon :as m]))

(defn config [k]
  (config/get-config [:providers :clubhouse k]))

(defn endpoint []
  (str "https://api.clubhouse.io/api/v3/stories/search?token="
       (config :access-token)))

(def date-fields #{:created_at :started_at :completed_at :updated_at :moved_at})
(def story-types #{"bug" "feature" "chore"})

(defn fetch-stories []
  (:body (http/post (endpoint)
                    {:body         {:story_type "bug"}
                     :content-type :json
                     :accept       :json
                     :as           :json-strict})))

(defn read-json [type]
  (->> (io/file "data" (str type ".json"))
       (slurp)
       (#(json/parse-string % true))
       (map #(util/parse-dates % date-fields))))

(defn fetch-all []
  (let [{:keys [out err]} (sh/with-sh-env
                            {:CLUBHOUSE_API_TOKEN (config :access-token)}
                            (sh/sh "./exporter.sh"))]
    (println out)
    (println err)))

;; TODO: just replace with functions
(def *members (delay (read-json "members")))
(def *members-by-id (delay (util/index-by :id @*members)))
(defn members-by-id [x]
  (get @*members-by-id x))

(def *features (delay (read-json "stories.features")))
(def *bugs (delay (read-json "stories.bugs")))
(def *chores (delay (read-json "stories.chores")))
(def *all-stories (delay (concat @*features @*bugs @*chores)))

(def remove-archived-incomplete
  (s/rewrite
    ((m/or {:archived  true
            :completed false}
           !stories) ...)
    ;;>
    (!stories ...)))

(defn completed-stories [stories]
  (cons ["completed_at" "owned_by" "type" "title" "url"]
        (for [{:keys [app_url story_type name completed_at owner_ids]} stories
              owner_id owner_ids
              :let [member (-> owner_id members-by-id :profile :mention_name)]]
          [(util/est-date completed_at) member story_type name app_url])))

(defn requested-stories [stories]
  (cons ["created_at" "requested_by" "type" "title" "url"]
        (for [{:keys [app_url story_type name requested_by_id created_at]} stories
              :let [member (-> requested_by_id members-by-id :profile :mention_name)]]
          [(util/est-date created_at) member story_type name app_url])))

(defn all-completed-stories []
  (->> @*all-stories
       (filter :completed_at)
       (sort-by :completed_at)
       (completed-stories)))

(defn all-requested-stories []
  (->> @*all-stories
       (filter :requested_by_id)
       (sort-by :created_at)
       (requested-stories)))

(defn story-lead-days [{:keys [completed_at created_at]}]
  (/ (t/time-between (t/instant created_at) (t/instant completed_at) :seconds)
     60.0 60.0 24.0))

(defn story-turnaround-days [{:keys [completed_at started_at]}]
  (/ (t/time-between (t/instant started_at) (t/instant completed_at) :seconds)
     60.0 60.0 24.0))

(defn bucket-stats [stories scalar-fn]
  (util/stats-bucket-rows :completed_at scalar-fn (filter :completed_at stories)))

(defn open? [date {:keys [created_at completed_at]}]
  (and (t/before? (t/instant created_at) (t/instant date))
       (or (nil? completed_at)
           (t/before? (t/instant date) (t/instant completed_at)))))

(defn closed? [date {:keys [completed_at]}]
  (and completed_at
       (t/before? (t/instant completed_at) (t/instant date))))

(defn in-progress? [date {:keys [completed_at started_at]}]
  (and started_at
       (t/before? (t/instant started_at) (t/instant date))
       (or (nil? completed_at)
           (t/before? (t/instant date) (t/instant completed_at)))))

(defn open-vs-closed
  "Pseudo historic (based on current data, not as-of)"
  [stories]
  (let [min-date (apply t/min (map :created_at stories))
        end      (t/java-date (util/end-of-week))
        dates    (reverse (util/periodic-seq end (t/days 1) min-date))]
    (loop [[date :as dates] dates
           [next-created :as by-created-at] (sort-by :created_at stories)
           [next-completed :as by-completed-at] (sort-by :completed_at (filter :completed_at stories))
           [next-started :as by-started-at] (sort-by :started_at (filter :started_at stories))
           open        #{}
           closed      #{}
           in-progress #{}
           result      [["date" "open" "closed" "in-progress"]]]
      (if dates
        (let [created?    (and (:created_at next-created)
                               (t/before? (t/instant (:created_at next-created)) date))
              open        (conj open next-created)
              started?    (and (:started_at next-started)
                               (t/before? (t/instant (:started_at next-started)) date))
              in-progress (conj in-progress next-started)

              completed?  (and (:completed_at next-completed)
                               (t/before? (t/instant (:completed_at next-completed)) date))
              closed      (conj closed next-completed)
              open        (disj open next-completed)
              in-progress (disj in-progress next-completed)

              more?       (or created? started? completed?)]
          (recur (if more?
                   dates
                   (next dates))
                 (if created?
                   (next by-created-at)
                   by-created-at)
                 (if completed?
                   (next by-completed-at)
                   by-completed-at)
                 (if started?
                   (next by-started-at)
                   by-started-at)
                 open
                 closed
                 in-progress
                 (if more?
                   result
                   (conj result
                         [(util/est-date date)
                          (count open)
                          (count closed)
                          (count in-progress)]))))
        result))))
