(ns chartit.gsheet-test
  (:require [chartit.gsheet :as gsheet]
            [clojure.test :refer [deftest testing is]]))

(defonce test-sheet-title "gsheet test")
(defonce test-id (gsheet/ensure-spreadsheet test-sheet-title))

(deftest set-data-test
  (is (gsheet/set-data test-id "Sheet1" [[99]])))

(deftest get-sheet-test
  (is (gsheet/get-sheet test-id "meta")))

(deftest create-test
  ;; would create duplicates
  #_(is (gsheet/create-spreadsheet test-sheet-title))
  ;; already exists
  #_(is (gsheet/create-sheet test-id "meta")))

(deftest ensure-sheet-test
  (is (gsheet/ensure-sheet test-id "meta")))

(deftest set-sheet-data-test
  (is (gsheet/set-sheet-data test-id "Sheet1" [[99]])))

(deftest get-spreadsheet-test
  (is (gsheet/find-spreadsheet test-sheet-title)))

(deftest ensure-spreadsheet-test
  (is (gsheet/ensure-spreadsheet test-sheet-title)))

(deftest pull-request-velocity-chart-spec-test
  (is (gsheet/rolling-averages-chart-spec 1182968322 "title" 100)))

(deftest create-velocity-chart-test
  (let [{{:keys [sheetId]} :properties, [{:keys [chartId]}] :charts}
        (gsheet/ensure-sheet test-id "chart")]
    (is (gsheet/create-velocity-chart test-id sheetId chartId "Test chart" 100))))
