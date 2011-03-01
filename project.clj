(defproject usable-az "1.0.0-SNAPSHOT"
  :description "Making AgileZen halfway usable."
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [clj-http "0.1.3"]
                 [org.apache.lucene/lucene-core "3.0.3"]
                 [org.danlarkin/clojure-json "1.1"]]
  :main ^{:skip-aot true} usable-az.core)
