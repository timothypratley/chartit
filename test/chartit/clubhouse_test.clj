(ns chartit.clubhouse-test
  (:require [chartit.clubhouse :as clubhouse]
            [clojure.test :refer [deftest is]]))

(deftest bucket-stats-test
  (is (clubhouse/bucket-stats clubhouse/all-stories clubhouse/story-lead-days))
  (is (clubhouse/bucket-stats clubhouse/all-stories clubhouse/story-turnaround-days)))

(deftest open-vs-closed-test
  (time (clubhouse/open-vs-closed (clubhouse/remove-archived-incomplete clubhouse/bugs))))

(deftest fetch-stories-test
  ;; TODO
  #_(count (clubhouse/fetch-stories)))
