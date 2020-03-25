(ns chartit.config
  (:require [clojure.edn :as edn]))

;; TODO: if this file changes I want to re-read it!
(def config (edn/read-string (slurp "config.edn")))

(defn get-config [ks]
  (get-in config ks))
