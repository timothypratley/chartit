(ns chartit.justworks-test
  (:require [chartit.justworks :as justworks]
            [clojure.test :refer [deftest is]]))

(deftest company-directory-test
  (is (seq (justworks/company-directory))))
