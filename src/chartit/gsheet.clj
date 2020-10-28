(ns chartit.gsheet
  (:require [chartit.config :as c]
            [happy.oauth2-credentials :as credentials]
            [happygapi.drive.files :as g.files]
            [happygapi.sheets.spreadsheets :as g.sheets]))

(defn init! []
  (credentials/init! (c/get-config [:providers :google :secret])
                     (c/get-config [:providers :google :scopes])))

(defn nap
  "Sleep a bit to avoid rate limiting"
  []
  (Thread/sleep 1000))

(defn retry
  [retries f & args]
  (let [res (try {:value (apply f args)}
                 (catch Exception e
                   (println "Retrying...")
                   (if (zero? retries)
                     (throw e)
                     {:exception e})))]
    (if (:exception res)
      (recur (dec retries) f args)
      (:value res))))

(defn batch-update [spreadsheet-id range values]
  (retry 3 (fn []
             (g.sheets/values-batchUpdate$ (credentials/auth!)
                                           {:spreadsheetId spreadsheet-id}
                                           {"valueInputOption" "USER_ENTERED"
                                            "data"             [{"range"  range
                                                                 "values" values}]}))))

(defn set-data [spreadsheet-id range values]
  (nap)
  (-> (batch-update spreadsheet-id range values)
      (doto (-> (:responses)
                (first)
                (:updatedRange)
                (println "updated")))))

(defn get-sheet [spreadsheet-id title]
  (nap)
  (->> (g.sheets/get$ (credentials/auth!)
                      {:spreadsheetId spreadsheet-id})
       (:sheets)
       (filter #(= (get-in % [:properties :title]) title))
       (first)))

(defn create-sheet [spreadsheet-id title]
  (nap)
  (-> (g.sheets/batchUpdate$ (credentials/auth!)
                             {:spreadsheetId spreadsheet-id}
                             {"requests" [{"addSheet" {"properties" {"title" title}}}]})
      (:replies)
      (first)
      (:addSheet)))

(defn ensure-sheet [spreadsheet-id title]
  (or (get-sheet spreadsheet-id title)
      (create-sheet spreadsheet-id title)))

(defn set-sheet-data [spreadsheet-id title values]
  (ensure-sheet spreadsheet-id title)
  (set-data spreadsheet-id title values))

(defn create-spreadsheet [title]
  (nap)
  (-> (g.sheets/create$ (credentials/auth!)
                        {}
                        {:properties {:title title}})
      (:spreadsheetId)))

(defn find-spreadsheet [title]
  (nap)
  (-> (g.files/list$ (credentials/auth!)
                     {"q" (format "mimeType='application/vnd.google-apps.spreadsheet' and name='%s'" title)})
      (:files)
      (first)
      (:id)))

(defn ensure-spreadsheet [title]
  (or (find-spreadsheet title)
      (create-spreadsheet title)))

(defn rolling-averages-chart-spec [sheet-id title end-row-index]
  {"title"                   title
   "fontName"                "Roboto",
   "hiddenDimensionStrategy" "SKIP_HIDDEN_ROWS_AND_COLUMNS"
   "basicChart"              {"axis"        [{"position"          "BOTTOM_AXIS",
                                              "title"             "date",
                                              "viewWindowOptions" {}}
                                             {"position" "LEFT_AXIS", "viewWindowOptions" {}}],
                              "chartType"   "LINE",
                              "domains"     [{"domain" {"sourceRange" {"sources" [{"endColumnIndex"   1,
                                                                                   "endRowIndex"      end-row-index,
                                                                                   "sheetId"          sheet-id,
                                                                                   "startColumnIndex" 0,
                                                                                   "startRowIndex"    0}]}}}],
                              "headerCount" 1,
                              "series"      [{"series"     {"sourceRange" {"sources" [{"endColumnIndex"   2,
                                                                                       "endRowIndex"      end-row-index,
                                                                                       "sheetId"          sheet-id,
                                                                                       "startColumnIndex" 1,
                                                                                       "startRowIndex"    0}]}},
                                              "targetAxis" "LEFT_AXIS"}
                                             {"series"     {"sourceRange" {"sources" [{"endColumnIndex"   3,
                                                                                       "endRowIndex"      end-row-index,
                                                                                       "sheetId"          sheet-id,
                                                                                       "startColumnIndex" 2,
                                                                                       "startRowIndex"    0}]}},
                                              "targetAxis" "LEFT_AXIS"}
                                             {"series"     {"sourceRange" {"sources" [{"endColumnIndex"   4,
                                                                                       "endRowIndex"      end-row-index,
                                                                                       "sheetId"          sheet-id,
                                                                                       "startColumnIndex" 3,
                                                                                       "startRowIndex"    0}]}},
                                              "targetAxis" "LEFT_AXIS"}
                                             {"series"     {"sourceRange" {"sources" [{"endColumnIndex"   5,
                                                                                       "endRowIndex"      end-row-index,
                                                                                       "sheetId"          sheet-id,
                                                                                       "startColumnIndex" 4,
                                                                                       "startRowIndex"    0}]}},
                                              "targetAxis" "LEFT_AXIS"}]}})

(defn pull-request-velocity-chart [sheet-id title number-of-rows]
  {"chart" {"position" {"overlayPosition" {"widthPixels"  1100
                                           "heightPixels" 550
                                           "anchorCell"   {"rowIndex"    2
                                                           "columnIndex" 0
                                                           "sheetId"     sheet-id}}}
            "spec"     (rolling-averages-chart-spec sheet-id title (inc number-of-rows))}})

(defn create-chart
  "If chart-id supplied, will delete and recreate, otherwise creates new"
  [spreadsheet-id chart-id chart]
  (nap)
  (-> (g.sheets/batchUpdate$
        (credentials/auth!)
        {:spreadsheetId spreadsheet-id}
        {:requests (if chart-id
                     [{:deleteEmbeddedObject {:objectId chart-id}}
                      {:addChart chart}]
                     [{:addChart chart}])})
      (:replies)
      (last)
      (:addChart)))

(defn create-velocity-chart [spreadsheet-id sheet-id chart-id title number-of-rows]
  (create-chart spreadsheet-id chart-id (pull-request-velocity-chart sheet-id title number-of-rows)))
