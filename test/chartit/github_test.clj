(ns chartit.github-test
  (:require [chartit.github :as github]
            [clojure.test :refer [deftest is testing]]
            [clj-time.core :as t]
            [chartit.config :as c]))

(def access-token (c/get-config [:providers :github :access-token]))

(deftest users-test
  (is (contains? (set (map :name (github/users* "clojure" access-token)))
                 "Rich Hickey")))

(deftest pull-requests-test
  (is (seq (map github/groom-pull-request
                (github/pull-requests "clubhouse" (t/date-time 2020 1 1) access-token)))))

(deftest reviews-test
  (let [pull-requests (github/pull-requests "clubhouse" (t/date-time 2020 1 1) access-token)
        reviews (github/reviews pull-requests)]
    (is (seq (map github/groom-pull-request pull-requests)))
    (is (seq (map github/groom-review reviews)))))

(deftest label-periods-test
  (is (= {:mergedAt "2020-01-01"
          :month    "2020-01"
          :quarter  "2020-Q1"
          :week     "2020-week01"
          :year     "2020"}
         (github/label-periods {:mergedAt (t/date-time 2020 1 1 23)}
                               :mergedAt))))
