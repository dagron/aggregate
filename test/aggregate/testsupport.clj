(ns aggregate.testsupport
  (:require [java-jdbc.ddl :as ddl]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :refer [join]]))

;; -------------------------------------------------------------------
;; Support code for tests

(def db-spec {:classname "org.h2.Driver"
              :subprotocol "h2"
              :subname "mem:test"
              :user "sa"
              :password ""})

(defonce db-con (atom nil))


(defn db-fixture
  [test-fn]
  (jdbc/with-db-connection [con db-spec]
    (reset! db-con con)
    (test-fn)
    (reset! db-con nil)))


(defn id-column
  []
  [:id "integer generated by default as identity primary key"])


(defn fk-column
  ([table owned?]
     (fk-column table (keyword (str (name table) "_id")) owned?))
  ([table column owned?]
     [column (str "integer"
                  (if owned? " not null")
                  " references " (name table) " (id)"
                  (if owned? " on delete cascade on update cascade"))]))


(defn create-schema!
  [db-spec schema]
  (doseq [t (->> schema (partition 2) (map (partial apply cons)))]
    (jdbc/execute! db-spec [(apply ddl/create-table t)]))
  (jdbc/execute! db-spec ["create sequence pkseq"]))


(defn record-count
  [con tablename]
  (->> (jdbc/query con [(str "select count(*) from " (name tablename))])
       first vals first))


(defn all-records
  [con tablename]
  (jdbc/query con [(str "select * from " (name tablename))]))


(defn width
  ([n s]
     (width n " " s))
  ([n padchar s]
     (let [l (count s)]
       (if (< l n)
         (apply str (cons s (repeat (- n l) padchar)))
         (.substring s 0 n)))))


(defn pr-line
  ([format str-value-fn]
     (pr-line format " " str-value-fn))
  ([format padchar str-value-fn]
     (->> format
          (map (fn [[kw length]]
                 (width length padchar (str-value-fn kw))))
          (join "|"))))


(defn pr-records
  [format row-maps]
  (->> row-maps
       (map (fn [m]
              (pr-line format (comp str m))))
       (cons (pr-line format "-" (constantly "")))
       (cons (pr-line format name))
       (join "\n")))


(defn dump-table
  [con tablename format]
  (->> (all-records con tablename)
       (pr-records format)
       println))
