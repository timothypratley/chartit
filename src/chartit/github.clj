(ns chartit.github
  (:require [chartit.config :as c]
            [chartit.graphql :as graphql]
            [chartit.util :as util]
            [clj-http.client :as http]
            [clojure.java.io :as io]
            [java-time :as t]))

(def endpoint "https://api.github.com/graphql")
(def queries (graphql/parse-or-throw (io/resource "github.graphql")))
(def date-fields #{:createdAt :updatedAt :mergedAt})
(def bot-logins #{"dependabot" "dependabot-preview" "ClubhouseBot"})

(defn fetch
  "Executes a graphql query to Github"
  [k args]
  (let [access-token (c/get-config [:providers :github :access-token])
        q (get-in queries [:query k])
        {:keys [graphql unpack]} (q args)
        response (http/post endpoint
                            {:headers {:authorization (str "Bearer " access-token)}
                             :form-params graphql
                             :content-type :json
                             :accept :json
                             :as :json})]
    (-> response :body unpack :data)))

(defn pull-requests
  [after]
  (-> (fetch :search-pull-request
             {:query (str "org:" (c/get-config [:providers :github :organization])
                          " is:pr is:merged sort:updated-asc"
                          (when after
                            (str " updated:>" (t/format (t/instant after)))))})
      (get-in [:search :nodes])
      (->> (remove #(-> % :author :login #{"dependabot" "dependabot-preview" "ClubhouseBot"}))
           (map #(util/fix-dates % date-fields)))
      (doto (-> (count) (println "pull requests fetched after" after)))))

;; TODO: assumes every PR has a unique updateAt, is this valid?
(defn all-pull-requests
  "Fetches all pull requests after a given point (nil for everything)."
  [after]
  (let [results (pull-requests after)]
    (when (seq results)
      (let [after (-> results last :updatedAt)]
        (concat results
                (lazy-seq (all-pull-requests after)))))))

(defn users
  "Fetches users who are members of an organization"
  [org]
  (-> (fetch :users
             {:org org})
      (get-in [:organization :membersWithRole :nodes])
      (doto (-> (count) (println "users fetched")))))

(defn groom-pull-request [m]
  (-> m
      (select-keys [:mergedAt :author :url :title])
      (util/label-periods :mergedAt)))

(defn pull-requests-as-rows [pull-requests]
  (graphql/nodes2rows (map groom-pull-request pull-requests)))

(defn pull-request-by-bot? [{{:keys [login]} :author}]
  (contains? bot-logins login))

(defn dedupe-updated-pull-requests
  "Keeps the oldest pull-request"
  [pull-requests]
  ;; URL serves as a unique identifier,
  ;; note that you can also request an explicit ID from github
  (->> pull-requests
       (reverse)
       (util/distinct-by :url)
       (reverse)))

;; TODO: find all PR approvals / requests for changes / SLA
