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

(def stories-url "https://agilezen.com/api/v1/projects/%s/stories?page=%s")

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

(def fields [:status :creator :text :phase :color :owner :size :priority :id])

(defn make-field [field-name story]
  (when-let [value (field-name story)]
    (Field. (name field-name) (if (map? value)
                                (:name value)
                                (str value))
            Field$Store/YES Field$Index/ANALYZED)))

(defn make-document [story]
  (let [doc (Document.)]
    (doseq [field-name fields]
      (when-let [field (make-field field-name story)]
        (.add doc field)))
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
    (reduce #(assoc %1 %2 (first (.getValues doc (name %2))))
            {} fields)))

(defn search [project-id key value]
  (with-open [r (IndexReader/open (make-directory project-id))
              searcher (IndexSearcher. r)]
    (pp/pprint (take (:max-results (config) 25)
                     (results searcher
                              (.search searcher (make-query key value)
                                       10000))))))

(defn usage []
  (println (str "Usage: lein run index PROJECT_NAME\n" "\n  or\n"
                "lein run search PROJECT_NAME KEY VALUE")))

(defn -main [command project-name & [key value]]
  (let [project-id (get-in (config) [:projects project-name])]
    (condp = command
        "index" (index-project project-id)
        "search" (if value
                   (search project-id key value)
                   (search project-id "text" key))
        :>> (usage))))