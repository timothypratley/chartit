(ns chartit.github
  (:require [chartit.config :as c]
            [chartit.graphql :as graphql]
            [chartit.stats :as stats]
            [chartit.util :as util]
            [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [java-time :as t]))

(def endpoint "https://api.github.com/graphql")
(def queries (graphql/parse-or-throw (io/resource "github.graphql")))
(def date-fields #{:createdAt :updatedAt :mergedAt :submittedAt})

(defn config [k]
  (c/get-config [:providers :github k]))

(defn parse-dates [m]
  (util/parse-dates m date-fields))

(defn fetch
  "Executes a graphql query to Github"
  [k args access-token]
  (let [q (get-in queries [:query k])
        {:keys [graphql unpack]} (q args)
        response (http/post endpoint
                            {:headers      {:authorization (str "Bearer " access-token)}
                             :form-params  graphql
                             :content-type :json
                             :accept       :json
                             :as           :json})]
    (-> response :body unpack :data)))

(defn pull-requests
  "See resources/github.graphql for query definition"
  [org after access-token]
  (let [after-str (when after
                    (t/format (t/instant after)))]
    (-> (fetch :search-pull-request
               {:query (str "org:" org
                            " is:pr is:merged sort:updated-asc"
                            (str/join (for [login (config :exclude-logins)]
                                        (str " -author:" login)))
                            (when after
                              (str " updated:>" after-str)))}
               access-token)
        (get-in [:search :nodes])
        (->> (map parse-dates))
        (doto (-> (count) (println "pull requests fetched after" after-str))))))

;; TODO: assumes every PR has a unique updateAt, handle collisions
(defn all-pull-requests*
  [org after access-token]
  (let [results (pull-requests org after access-token)]
    (when (seq results)
      (let [after (-> results last :updatedAt)]
        (concat results
                (lazy-seq (all-pull-requests* org after access-token)))))))

(defn all-pull-requests
  "Fetches all pull requests after a given point (nil for everything)."
  [after]
  (all-pull-requests* (config :organization) after (config :access-token)))

(defn users*
  [org access-token]
  (-> (fetch :users {:org org} access-token)
      (get-in [:organization :membersWithRole :nodes])
      (doto (-> (count) (println "users fetched")))))

(defn users
  "Fetches users who are members of the configured organization"
  []
  (users* (config :organization) (config :access-token)))

(defn active-repositories*
  [org access-token]
  (-> (fetch :active-repositories {:query (str "org:" org " archived:false")} access-token)
      (get-in [:search :nodes])
      (doto (-> (count) (println "active repositories fetched")))))

(defn active-repositories
  "Fetches users who are members of the configured organization"
  []
  (active-repositories* (config :organization) (config :access-token)))


(defn earliest [reviews]
  (when (seq reviews)
    (apply t/min (map :submittedAt reviews))))

(defn latest [reviews]
  (when (seq reviews)
    (apply t/max (map :submittedAt reviews))))

(defn label-periods [m k]
  (let [at (some-> (get m k) (util/est))]
    (if at
      (assoc m k (util/year-month-day at)
               :week (util/year-week at)
               :month (util/year-month at)
               :quarter (util/year-quarter at)
               :year (util/year at))
      m)))

(defn calc-pull-request-review-hours [pull-request]
  (let [reviews (->> (get-in pull-request [:reviews :nodes])
                     (map parse-dates))
        reviewed-at (earliest reviews)
        approved-at (->> reviews
                         (filter #(= "APPROVED" (:state %)))
                         (latest))
        created-at (:createdAt pull-request)
        merged-at (:mergedAt pull-request)]
    {:hoursToReview  (util/hours-between created-at reviewed-at)
     :hoursToApprove (util/hours-between created-at approved-at)
     :hoursToMerge   (util/hours-between created-at merged-at)}))

(defn calc-review-review-hours [review]
  (let [pull-request (:pull-request review)
        reviewed-at (:submittedAt review)
        created-at (:createdAt pull-request)
        merged-at (:mergedAt pull-request)]
    {:hoursToReview (util/hours-between created-at reviewed-at)
     :hoursToMerge  (util/hours-between created-at merged-at)}))

(defn groom-pull-request [pull-request]
  (-> pull-request
      (select-keys [:mergedAt :author :assignees :url :title])
      (update :assignees (fn [assignees]
                           (str/join " " (map :login (:nodes assignees)))))
      (merge (calc-pull-request-review-hours pull-request))
      (label-periods :mergedAt)))

(defn pull-requests-as-rows [pull-requests]
  (graphql/nodes2rows (map groom-pull-request pull-requests)))

(defn dedupe-updated-pull-requests
  "Keeps the oldest pull-request"
  [pull-requests]
  ;; URL serves as a unique identifier,
  ;; note that you can also request an explicit ID from github
  (->> pull-requests
       (reverse)
       (util/distinct-by :url)
       (reverse)))

(defn reviews [pull-requests]
  (->> (for [pull-request pull-requests
             review (get-in pull-request [:reviews :nodes])
             :when (:submittedAt review)]
         (-> review
             (parse-dates)
             (assoc :pull-request pull-request)))
       (sort-by :submittedAt)))

(defn groom-review [review]
  (-> review
      (select-keys [:submittedAt :author :url])
      (merge (calc-review-review-hours review))
      (label-periods :submittedAt)))

(defn reviews-as-rows [reviews]
  (graphql/nodes2rows (map groom-review reviews)))

(defn bucket-pull-requests [pull-requests]
  (util/buckets2rows
    (util/bucket-by :mergedAt count pull-requests)))

(defn bucket-pull-requests-with-tenure [pull-requests]
  (util/buckets2rows
    (util/bucket-tenure pull-requests)))

(defn bucket-reviews [reviews]
  (util/buckets2rows
    (util/bucket-by :submittedAt count reviews)))

;; TODO: complects field with stat, and should create rolling average for each stat??
(defn bucket-review-times [reviews]
  (util/buckets2rows
    (let [scalar-fn (fn [reviews]
                      (let [review-hours (->> reviews
                                              (map calc-review-review-hours)
                                              (map :hoursToReview)
                                              (remove nil?))]
                        (if (seq review-hours)
                          (stats/mean review-hours)
                          0.0)))]
      (util/bucket-by :submittedAt scalar-fn reviews))))

(def *login-group-pairs
  (delay
    (for [[login groups] (config :login-groups)
          group groups
          login-group (cons [login group]
                            (for [[g1 gs] (config :group-groups)
                                  g2 gs
                                  :when (= group g2)]
                              [login g1]))]
      login-group)))

(def *login-groups
  (delay
    (reduce
      (fn [acc [login group]]
        (update acc login (fnil conj #{}) group))
      {}
      @*login-group-pairs)))

(defn login-groups [login]
  (get @*login-groups login))

(def *group-logins
  (delay
    (reduce
      (fn [acc [login group]]
        (update acc group (fnil conj #{}) login))
      {}
      @*login-group-pairs)))

(defn group-logins [group]
  (get @*group-logins group))
