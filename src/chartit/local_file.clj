(ns chartit.local-file
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [fipp.edn :as fipp])
  (:import (java.io File)))

(def folder "data")

(defn init []
  (.mkdirs (io/file folder)))

(defn location ^File [k]
  (io/file folder (str (name k) ".edn")))

(defn load [k]
  (let [file (location k)]
    (when (.exists file)
      (edn/read-string (slurp file)))))

(defn save [k pull-requests]
  (init)
  (spit (location k)
        (with-out-str (fipp/pprint pull-requests))))
