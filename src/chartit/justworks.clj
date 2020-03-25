(ns chartit.justworks
  "To update navigate to https://secure.justworks.com/directory,
  copy the members JSON to data/company-directory.json"
  (:require #_[clojure.string :as str]
            #_[hickory.core :as h]
            #_[meander.epsilon :as m]
            #_[meander.strategy.epsilon :as s]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(def company-directory-page "directory.html")

#_
(defn fetch-as-hiccup
  "Read the source an parse it into a hiccup tree"
  [source]
  (-> (slurp source)
      (h/parse)
      (h/as-hiccup)))

#_
(def extract-employees
  (s/search
   (m/$
    [:div {:class "directory-wrapper"} &
     (m/scan
      ;; heading/table pairs
      [:h3 {} ?department & _]
      _
      [:table &
       (m/scan
        [:tbody &
         (m/scan
          [:tr &
           (m/separated
            [:td & (m/scan [:a {} ?name & _])]
            [:td {} ?title & _]
            [:td & (m/scan [:a {:href ?mailto} & _])])])])])])
   ;;=>
   {:department (str/trim ?department)
    :name ?name
    :title ?title
    :email (subs ?mailto 7)}))

#_
(defn company-directory
  "Returns employee details as entities"
  []
  (extract-employees
   (fetch-as-hiccup company-directory-page)))

(defn company-directory []
  (let [company-directory-file (io/file "data" "company-directory.json")]
    (when (.exists company-directory-file)
      (json/parse-string (slurp company-directory-file)))))
