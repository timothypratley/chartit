(ns chartit.github-test
  (:require [chartit.github :as github]
            [clojure.test :refer [deftest is testing]]))

(deftest users-test
  (is (contains? (set (map :name (github/users "clojure")))
                 "Rich Hickey")))
