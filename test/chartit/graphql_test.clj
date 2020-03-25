(ns chartit.graphql-test
  (:require [chartit.graphql :as graphql]
            [clojure.test :refer [deftest is]]))

(deftest nodes2rows-test
  (is (= '((:a)
           (:b :c)
           (:d :e :f)
           (:d :e :g))
         (graphql/unpack-keys {:a "A" :b {:c "C"} :d {:e {:f "F" :g "G"}}})))
  (is (= '(["foo"
            "baz_booz"]
           ["bar"
            "booz"])
         (graphql/nodes2rows [{:foo "bar" :baz {:booz "booz"}}]))))
