(ns chartit.util-test
  (:require [chartit.util :as util]
            [clojure.test :refer [deftest is]]
            [java-time :as t]))

(deftest distinct-by-test
  (is (= '({:a "a"
            :k 1}
           {:k 2})
         (util/distinct-by :k
                           [{:k 1, :a "a"}
                            {:k 1, :a "b"}
                            {:k 2}]))))

(deftest year-week-test
  (is (= "2020-week01"
         (util/year-week (t/local-date 2020 1 3)))))

(deftest year-quarter-test
  (is (= "2020-Q1"
         (util/year-quarter (t/local-date 2020 1 3)))))

(deftest end-of-week-test
  (is (util/end-of-week)))

(deftest periodic-seq-test
  (is (util/periodic-seq (util/end-of-week) (t/weeks 1) (t/minus (util/end-of-week) (t/years 1)))))

(deftest bucket-by-test
  (is (util/bucket-by :t count
                      [{:t (t/minus (util/end-of-week) (t/days 1))} {:t (util/end-of-week)}])))

(deftest stats-by-test
  (is (util/bucket-by :t #(util/stats-by :v %)
                      [{:t (t/minus (util/end-of-week) (t/days 8))
                        :v 42.0}
                       {:t (t/minus (util/end-of-week) (t/days 1))
                        :v 42.0}
                       {:t (util/end-of-week)
                        :v 11.1}])))

(deftest stats-bucket-rows-test
  (is (util/stats-bucket-rows :t :v
                              [{:t (t/minus (util/end-of-week) (t/days 1))
                                :v 42.0}
                               {:t (util/end-of-week)
                                :v 11.1}])))

(deftest with-rolling-test
  (is (= '([:a 1 nil]
           [:b 2 1.5]
           [:c 3 2.5]
           [:d 4 3.5]
           [:e 5 4.5])
         (util/with-rolling [[:a 1]
                             [:b 2]
                             [:c 3]
                             [:d 4]
                             [:e 5]]
                            2))))

(deftest group-by-groups-test
  (is (= {:a [{:groups #{:a :b}}
              {:groups #{:a :b :c}}
              {:groups #{:a :c}}]
          :b [{:groups #{:a :b}}
              {:groups #{:a :b :c}}]
          :c [{:groups #{:c}}
              {:groups #{:a :b :c}}
              {:groups #{:a :c}}]}
         (util/group-by-groups :groups [{:groups #{:a :b}}
                                        {:groups #{:c}}
                                        {:groups #{:a :b :c}}
                                        {:groups #{:a :c}}]))))
