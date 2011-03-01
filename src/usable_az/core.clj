(ns usable-az.core
  (:require [clj-http.client :as http]
            [org.danlarkin.json :as json]
            [clojure.java.io :as io]
            [clojure.pprint :as pp])
  (:import (org.apache.lucene.index IndexReader IndexWriter
                                    IndexWriter$MaxFieldLength Term)
           (org.apache.lucene.search IndexSearcher BooleanQuery
                                     PhraseQuery BooleanClause$Occur TermQuery)
           (org.apache.lucene.queryParser QueryParser)
           (org.apache.lucene.document Document Field Field$Store Field$Index)
           (org.apache.lucene.analysis SimpleAnalyzer)
           (org.apache.lucene.analysis.standard StandardAnalyzer)
           (org.apache.lucene.store SimpleFSDirectory FSDirectory)
           (org.apache.lucene.util Version)
           (org.apache.lucene.document Field Field$Store Field$Index)))

(def stories-url (str "https://agilezen.com/api/v1/projects/%s/stories?page=%s"
                      "&with=details,tasks,tags,comments"))

(defn config []
  (read-string (slurp (io/resource "config.clj"))))

(when-not (find-ns 'swank.swank)
  (alter-var-root #'config memoize))

(defn headers []
  {"X-Zen-ApiKey" (:api-key (config))})

(defn get-page [project-id & [page]]
  (-> (format stories-url project-id (or page 1))
      (http/get {:headers (headers)})
      :body
      json/decode))

(defn get-stories [project-id]
  (let [page1 (get-page project-id)]
    (mapcat (comp :items (partial get-page project-id))
            (range 1 (inc (:totalPages page1))))))

(defn make-directory [project-id]
  (-> (format (:index-path (config)) project-id)
      io/file
      SimpleFSDirectory.))

(defmacro with-writer [[name project-id] & body]
  `(with-open [~name (IndexWriter. (make-directory ~project-id)
                                   (StandardAnalyzer. Version/LUCENE_30) true
                                   IndexWriter$MaxFieldLength/UNLIMITED)]
     ~@body))

(def fields [:id :status :creator :details :text :phase :color :owner :size
             :priority :tasks :tags :comments])

(defn extract-value [value all-field]
  (let [value (if (map? value)
                (or (:text value) (:name value))
                (str value))]
    (swap! all-field str " " value)
    value))

(defn make-field [field-name value all-field]
  (Field. (name field-name) (extract-value value all-field)
          Field$Store/YES Field$Index/ANALYZED))

(defn make-document [story]
  (let [doc (Document.)
        ;; ick! would use a reduce here if I cared enough
        all-field (atom "")]
    (doseq [field-name fields]
      (when-let [value (field-name story)]
        (if (vector? value)
          (doseq [v value]
            (.add doc (make-field field-name v all-field)))
          (.add doc (make-field field-name value all-field)))))
    (.add doc (Field. "_all" @all-field Field$Store/NO Field$Index/ANALYZED))
    doc))

(defn index-story [writer story]
  (.updateDocument writer (Term. "id" (str (:id story))) (make-document story)))

(defn index-project [project-id]
  (with-writer [writer project-id]
    (doseq [story (get-stories project-id)]
      (index-story writer story))
    (.optimize writer)))

(defn- make-query [key value]
  (.parse (QueryParser. Version/LUCENE_30 key
                        (StandardAnalyzer. Version/LUCENE_30)) value))

(defn results [searcher top-docs]
  (for [score-doc (.scoreDocs top-docs)
        :let [doc (.doc searcher (.doc score-doc))]]
    (reduce #(assoc %1 %2 (.getValues doc (name %2)))
            {} fields)))

(defn search [project-id key value]
  (with-open [r (IndexReader/open (make-directory project-id))
              searcher (IndexSearcher. r)]
    (pp/pprint (take (:max-results (config) 25)
                     (results searcher
                              (.search searcher (make-query key value)
                                       10000))))))

(defn usage []
  (println "Usage: lein run index PROJECT_NAME\n\n"
           " or\n\nlein run search PROJECT_NAME KEY VALUE"))

(defn -main [command & [project-name key value]]
  (if-let [project-id (get-in (config) [:projects project-name])]
    (condp = command
        "index" (index-project project-id)
        "search" (if value
                   (search project-id key value)
                   (search project-id "_all" key))
        "show" (search project-id "id" key)
        :>> (usage))
    (println "You did not specify a known project. Try one of these:"
             (keys (:projects (config))))))
