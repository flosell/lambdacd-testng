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

(defn get-report-summary-as-string [report]
  (clojure.string/join ", " (map (fn [[k v]] (str (name k) ": " v)) (:attrs report))))

(defn select-all-classes [report]
  (let [result {:classes         (select [:content ALL #(= (:tag %) :suite) :content ALL #(= (:tag %) :test) :content ALL] report)
                :original-report report}]
    (if (empty? (:classes result))
      (throw (Exception. "Can't find any test classes"))
      result)))

(defn delete-empty-classes [classes]
  (filter #(not (empty? (:test-methods %))) classes))

(defn filter-classes-with-failed-methods [{classes :classes :as m}]
  (assoc m :classes
           (delete-empty-classes
             (into []
                   (map
                     (fn [cls] {:name (:name (:attrs cls)) :test-methods (select [:content ALL #(= "FAIL" (:status (:attrs %)))] cls)})
                     classes)))))

(defn restructure-exceptions [exs]
  (into [] (map (fn [ex] {:class   (get-in ex [:attrs :class])
                          :message (s/replace (s/trim (s/join "<br/>" (map #(s/join "<br/>" (:content %)) (:content ex)))) #" at " " at<br/>")}) exs)))

(defn restructure-test-method [tm]
  {:name       (get-in tm [:attrs :name])
   :exceptions (restructure-exceptions (filter #(= (:tag %) :exception) (:content tm)))})

(defn restructure-results [{classes :classes :as m}]
  (assoc m :classes (transform [ALL :test-methods ALL] restructure-test-method classes)))

(defn exception->details-map [ex]
  {:label (str (:class ex) " - " (:message ex))})

(defn test-method->details-map [tm]
  {:label   (:name tm)
   :details (into [] (map exception->details-map (:exceptions tm)))})

(defn restructure-class [cls]
  {:label   (:name cls)
   :details (into [] (map test-method->details-map (:test-methods cls)))})

(defn error-result [msg]
  {:label   "TestNG-Report"
   :details [{:label (str "Internal Error: " msg)}]})

(defn success-result [report]
  {:label   "TestNG-Report"
   :details [{:label   "Summary:"
              :details [{:label (get-report-summary-as-string (:original-report report))}]}
             (if (empty? (:classes report))
               {:label "There aren't any errors"}
               {:label   "Errors:"
                :details (into [] (map restructure-class (:classes report)))})]})

(defn get-testng-report-as-details [filename]
  (try
    (-> (parse-xml-file filename)
        (select-all-classes)
        (filter-classes-with-failed-methods)
        (restructure-results)
        (success-result))
    (catch Exception e
      (error-result (.getMessage e)))))