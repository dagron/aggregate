(ns aggregate.bookstore-test
  (:require [clojure.test :refer :all]
            [aggregate.core :as agg]
            [aggregate.testsupport :refer :all]
            [clojure.java.jdbc :as jdbc]))

(use-fixtures :each db-fixture)

(def ddl
  ["DROP TABLE IF EXISTS book, company, author, author_books"
   "CREATE TABLE book (id integer generated by default as identity primary key, book_name VARCHAR(80));"
   "CREATE TABLE company (id integer generated by default as identity primary key, company_name VARCHAR(80))"
   "CREATE TABLE author (id integer generated by default as identity primary key, author_name VARCHAR(80), company_id INTEGER REFERENCES company (id), book_id INTEGER REFERENCES book (id));"
   "CREATE TABLE author_books (author_id INTEGER NOT NULL REFERENCES author (id), book_id INTEGER NOT NULL REFERENCES book (id))"])


(defn- execute!
  [db-conn statements]
  (doseq [statement statements]
    (jdbc/execute! db-conn [statement])))



(def model
  (agg/make-er-config
   (agg/entity :book
               (agg/->mn :authors :author {:query-fn
                                           (agg/make-query-<many>-fn :author
                                                                     :author_books ; link table
                                                                     :book_id
                                                                     :author_id)
                                           :update-links-fn
                                           (agg/make-update-links-fn :author_books ; link table
                                                                     :book_id
                                                                     :author_id)}))
   (agg/entity :author
               (agg/->1 :company :company {:fk-kw  :company_id
                                           :owned? true})
               (agg/->1 :book :book {:fk-kw  :book_id
                                     :owned? true}))
   (agg/entity :company)))


;; Tests

(deftest with-link-to-existing-company-test
  (execute! @db-con ddl)
  (testing "Update existing book with an author linked to existing company"
    (let [model      (agg/without model [:author :book])
          book-id    (-> (agg/save! model @db-con :book {:book_name "The Great Gatsby" :authors []})
                         :id)
          company-id (-> (agg/save! model @db-con :company {:company_name "Fishbulb"})
                         :id)
          book       {:book_name "Moby Dick"
                      :authors   [{:author_name "Herman Melville"
                                   :company     {:id company-id}}]
                      :id        book-id}
          book'      (agg/save! model @db-con :book book)]
      (is (= {:book_name             "Moby Dick",
              :authors
              [{:author_name           "Herman Melville",
                :company               {:id 1, :aggregate.core/entity :company},
                :company_id            1,
                :id                    1,
                :aggregate.core/entity :author}],
              :id                    1,
              :aggregate.core/entity :book}
             book')))))


(deftest with-link-to-same-book-test
  (execute! @db-con ddl)
  (testing "Update existing book with an author linked to itself"
    (let [model   (agg/without model [:author :company])
          book-id (-> (agg/save! model @db-con :book {:book_name "The Great Gatsby" :authors []})
                      :id)
          book    {:book_name "Moby Dick"
                   :authors   [{:author_name "Herman Melville"
                                :book        {:id book-id}}]
                   :id        book-id}
          book'   (agg/save! model @db-con :book book)]
      (is (= {:book_name             "Moby Dick",
              :authors
              [{:author_name           "Herman Melville",
                :book                  {:id 1},
                :book_id               1,
                :id                    1,
                :aggregate.core/entity :author}],
              :id                    1,
              :aggregate.core/entity :book}
             book')))))


;; Testing in the REPL
(comment
  (do (require '[aggregate.h2 :as h2])
      (h2/start-db))

  (jdbc/with-db-connection [db-conn h2/db-spec]
    (execute! db-conn ddl)
    (def book-id (-> (agg/save! model db-conn :book {:book_name "The Great Gatsby" :authors []}) :id))
    (def company-id (-> (agg/save! model db-conn :company {:company_name "Fishbulb"}) :id))
    (def book {:book_name "Moby Dick"
               :authors [{:author_name "Herman Melville" :company {:id company-id}}]
               :id book-id})
    (def book' (agg/save! model db-conn :book book))))
