(ns usable-az.test.core
  (:use [usable-az.core] :reload)
  (:use [clojure.test]))

;; (def project-id 12433)

(deftest test-get-stories
  (is (not (empty? (get-stories project-id)))))

(alter-var-root #'get-stories memoize)

(deftest test-index-project
  (index-project 12433))
