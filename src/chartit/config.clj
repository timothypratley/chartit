(ns chartit.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def *config
  (delay (or (edn/read-string (System/getenv "CHARTIT_CONFIG"))
             (let [c (io/file "config.edn")]
               (when (.exists c)
                 (edn/read-string (slurp c))))
             (throw (ex-info "Missing configuration, supply in environment CHARTIT_CONFIG or config.edn" {})))))

(defn get-config [ks]
  (get-in @*config ks))
