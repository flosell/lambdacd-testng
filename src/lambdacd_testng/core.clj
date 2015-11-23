(ns lambdacd-testng.core
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]
            [com.rpl.specter :refer :all]
            [clojure.string :as s]))

(defn parse-xml-file [filename]
  (-> (slurp filename)
      (.getBytes)
      (java.io.ByteArrayInputStream.)
      (xml/parse)
      (zip/xml-zip)
      (first)))

(defn get-report-summary [d]
  (:attrs d))

(defn select-all-classes [report]
  {:classes (select [:content ALL #(= (:tag %) :suite) :content ALL #(= (:tag %) :test) :content ALL] report)
   :report  report})

(defn delete-empty-classes [classes]
  (filter #(not (empty? (:test-methods %))) classes))

(defn filter-classes-with-failed-methods [{classes :classes :as m}]
  (assoc m :classes (delete-empty-classes (into [] (map (fn [cls] {:name (:name (:attrs cls)) :test-methods (select [:content ALL #(not (= "PASS" (:status (:attrs %))))] cls)}) classes)))))

(defn restructure-exceptions [exs]
  (into [] (map (fn [ex] {:class   (get-in ex [:attrs :class])
                          :message (s/trim (s/join "" (map #(s/join "" (:content %)) (:content ex))))}) exs)))

(defn restructure-test-method [tm]
  {:name       (get-in tm [:attrs :name])
   :exceptions (restructure-exceptions (filter #(= (:tag %) :exception) (:content tm)))})

(defn restructure-results [{classes :classes :as m}]
  (assoc m :classes (transform [ALL :test-methods ALL] restructure-test-method classes)))

(defn get-classes-with-failed-methods [filename]
  (some-> (parse-xml-file filename)
          (select-all-classes)
          (filter-classes-with-failed-methods)
          (restructure-results)))

(defn exception->details-map [ex]
  {:label (str (:class ex) " - " (:message ex))})

(defn test-method->details-map [tm]
  {:label (:name tm)
   :details (into [] (map exception->details-map (:exceptions tm)))})

(defn restructure-class [cls]
  {:label (:name cls)
   :details (into [] (map test-method->details-map (:test-methods cls)))})

(defn map->artifact-structure [m]
  {:label "TestNG-Report"
   :details [{:label "Summary:"
             :details [{:label (get-report-summary (:report m))}]}
             {:label "Errors:"
              :details (into [] (map restructure-class (:classes m)))}]})