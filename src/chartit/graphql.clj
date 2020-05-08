(ns chartit.graphql
  (:require [clojure.string :as str]
            [graphql-builder.parser :as p]
            [graphql-builder.core :as b]
            [instaparse.core :as insta]))

(defn parse-or-throw
  "Attempts to parse GraphQL found in source.
  Returns a map representing the GraphQL on success.
  Throws a compiler exception on failure with a link to the source location."
  [source]
  (let [parsed (-> (slurp source)
                   (p/parse))]
    (if (insta/failure? parsed)
      (throw
       (let [{:keys [line column]} parsed]
         (clojure.lang.Compiler$CompilerException.
          (str source)
          line
          column
          (Exception. "GraphQL parsing failure."))))
      (b/query-map parsed))))

(defn fetch-all
  "Given a fetch function that takes a cursor, fetch all pages of data available.
  Does not yet support multiple cursors."
  [f & args]
  (loop [response (apply f nil args)
         acc ()]
    (let [{:keys [nodes pageInfo]} response
          {:keys [hasNextPage endCursor]} pageInfo
          acc (concat acc nodes)]
      (if hasNextPage
        (recur (apply f endCursor args)
               acc)
        acc))))

(defn unpack-keys
  "Convert nested map keys into a sequence of key sequences suitable for get-in."
  [m]
  (apply concat
         (for [[k v] m]
           (if (map? v)
             (map #(cons k %) (unpack-keys v))
             (list (list k))))))

(defn nodes2rows
  "GraphQL returns a graph that we want to turn into rows."
  [xs]
  (when (seq xs)
    (let [a (first xs)
          paths (unpack-keys a)]
      (cons
       ;; header
       (vec (for [path paths]
              (str/join "_" (map name path))))
       ;; rows
       (for [x xs]
         (vec (for [path paths]
                (get-in x path))))))))
