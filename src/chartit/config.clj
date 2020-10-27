(ns chartit.config
  (:require [clojure.edn :as edn]))

(def *config
  (delay (or (edn/read-string (System/getenv "CHARTIT_CONFIG"))
             (let [c (io/file "config.edn")]
               (when (.exists c)
                 (edn/read-string (slurp c))))
             (throw (ex-info "Missing configuration, supply in environment CHARIT_CONFIG or config.edn" {})))))

(defn get-config [ks]
  (get-in @*config ks))
