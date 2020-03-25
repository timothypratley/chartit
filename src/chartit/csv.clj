(ns chartit.csv
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]))

(defn write-csv [filename data]
  (with-open [writer (io/writer filename)]
    (csv/write-csv writer data)))
