(ns chartit.util
  (:require [clojure.instant :as instant]
            [java-time :as t]
            [incanter.stats :as stats]))

(defn index-by
  "Returns a map of the elements of coll keyed by the result of f on each
  element. The value at each key will be the last element in coll associated
  with that key. This function is similar to `clojure.core/group-by`, except
  that elements with the same key are overwritten, rather than added to a
  vector of values."
  [f coll]
  (persistent! (reduce #(assoc! %1 (f %2) %2) (transient {}) coll)))

(defn distinct-by
  "Returns a lazy sequence of the elements of coll with duplicates identified by `key-fn` removed.
  Keeps the first occurrence found."
  [key-fn coll]
  (let [step (fn step [xs seen]
               (lazy-seq
                 ((fn [[f :as xs] seen]
                    (when-let [s (seq xs)]
                      (let [k (key-fn f)]
                        (if (contains? seen k)
                          (recur (rest s) seen)
                          (cons f (step (rest s) (conj seen k)))))))
                  xs seen)))]
    (step coll #{})))

(defn est [date]
  (t/zoned-date-time date "America/New_York"))

(defn year-month-day [date]
  (t/format "yyyy-MM-dd" date))

(defn est-date [date]
  (year-month-day (est date)))

(defn year-week [date]
  (t/format "yyyy-'week'ww" date))

(defn year-month [date]
  (t/format "yyyy-MM" date))

(defn year-quarter [date]
  (str (t/format "yyyy-" date)
       "Q" (inc (quot (.getValue ^java.time.Month (t/month date)) 4))))

(defn year [date]
  (t/format "yyyy" date))

(defn end-of-week []
  (-> (t/zoned-date-time)
      (est)
      (t/adjust (t/day-of-week 7))
      (t/adjust (t/local-time 23 59 59))))

(defn periodic-seq [start-date period end-date]
  (take-while #(t/after? (t/instant %) (t/instant end-date))
              (t/iterate t/minus (t/instant start-date) period)))

(defn bucket-by
  "Given a `time-fn` which looks up a time from an entity,
  an `aggregate-fn` such as `count` to apply to each group of entities,
  and a sequence of `entities`,
  returns a sequence of [bucket (aggregate-fn entities-in-bucket)]."
  [time-fn aggregate-fn entities]
  (when (seq entities)
    (let [min-date (apply t/min (for [entity entities]
                                  (time-fn entity)))
          buckets (reverse (periodic-seq (t/java-date (end-of-week)) (t/weeks 1) min-date))]
      (loop [[bucket & more-buckets] buckets
             acc ()
             es (sort-by time-fn entities)]
        (let [boundary-fn #(not (t/after? (t/instant (time-fn %)) bucket))
              [bucketed-entities more-entities] (split-with boundary-fn es)
              acc (cons [bucket (aggregate-fn bucketed-entities)] acc)]
          (if more-buckets
            (recur more-buckets acc more-entities)
            (reverse acc)))))))

(defn calc-tenure [pull-requests]
  (reduce
    (fn [acc {:keys           [mergedAt]
              {:keys [login]} :author}]
      (if (contains? acc login)
        (assoc-in acc [login :last-mergedAt] mergedAt)
        (assoc acc login {:first-mergedAt mergedAt
                          :last-mergedAt  mergedAt})))
    {}
    (sort-by :mergedAt pull-requests)))

(defn bucket-tenure [pull-requests]
  (let [tenure (calc-tenure pull-requests)
        min-date (apply t/min (for [entity pull-requests]
                                (:mergedAt entity)))
        ts (reverse (periodic-seq (t/java-date (end-of-week)) (t/weeks 1) min-date))]
    (loop [[t & more-ts] ts
           acc ()
           es (sort-by :mergedAt pull-requests)]
      (let [boundary-fn #(not (t/after? (t/instant (:mergedAt %)) t))
            [bucketed-entities more-entities] (split-with boundary-fn es)
            current (filter (fn [[login {:keys [first-mergedAt last-mergedAt]}]]
                              (and
                                (t/before? (t/instant first-mergedAt) t)
                                (t/before? (t/minus t (t/weeks 1)) (t/instant last-mergedAt))))
                            tenure)
            pr-count (count bucketed-entities)
            person-count (count current)
            pr-per-person (when (pos? person-count)
                            (/ pr-count person-count))
            acc (cons [t pr-per-person]
                      acc)]
        (if more-ts
          (recur more-ts acc more-entities)
          (reverse acc))))))

(defn stats-by
  "Given a `scalar-fn` that looks up a numeric value in an entity,
  and a sequence of entities,
  returns min/max/median/standard-deviation for the sequence.
  Suitable for use with `bucket-by`."
  [scalar-fn entities]
  (when-let [xs (seq (map scalar-fn entities))]
    {:count              (count xs)
     :min                (apply min xs)
     :max                (apply max xs)
     :mean               (stats/mean xs)
     :median             (stats/median xs)
     :standard-deviation (stats/sd xs)}))

(defn buckets2rows [bucketed]
  (for [[bucket v] bucketed]
    `[~(est-date bucket) ~@(cond (map? v) (vals v)
                                 (vector? v) v
                                 :else [v])]))

(defn stats-bucket-rows [time-fn scalar-fn entities]
  (cons
    ["week" "count" "min" "max" "mean" "median" "standard-deviation"]
    (buckets2rows
      (bucket-by time-fn #(stats-by scalar-fn %) entities))))

(defn parse-dates
  "Convert date-fields to dates.
  Useful when handling JSON (which does not have a date type)."
  [x date-fields]
  (reduce (fn [acc k]
            (if (get acc k)
              (update acc k instant/read-instant-date)
              acc))
          x
          date-fields))

(defn with-rolling
  "Given a sequence of ([label scalar]) conjs on a moving average of the last n scalars."
  [rows n]
  (map conj rows (concat (repeat (dec n) nil)
                         (map #(/ (double (reduce + %)) n)
                              (partition n 1 (map second rows))))))

(defn round [x]
  (/ (Math/round (* 100.0 x)) 100.0))

(defn hours-between [a b]
  (when (and a b)
    (round (/ (t/time-between (t/instant a) (t/instant b) :seconds)
              60.0
              60.0))))

(defn group-by-groups
  "Returns a map of the elements of coll keyed by the result of
  f on each element. The value at each key will be a vector of the
  corresponding elements, in the order they appeared in coll."
  [f coll]
  (persistent!
    (reduce
      (fn [acc x]
        (let [ks (f x)]
          (reduce
            (fn [acc k]
              (assoc! acc k (conj (get acc k []) x)))
            acc
            ks)))
      (transient {})
      coll)))
