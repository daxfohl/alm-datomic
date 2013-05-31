(ns alm.peer
  (:require [datomic.api :as d :refer (q)]))

(def uri "datomic:mem://alm")

(def schema-tx (read-string (slurp "resources/alm/schema.edn")))

(defn init-db []
  (when (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema-tx))))

(defn get-catalogs []
  (init-db)
  (let [conn (d/connect uri)]
    (q '[:find ?c :where [?e :catalog/name ?c]] (d/db conn))))

(defn get-fields []
  (let [conn (d/connect uri)]
    (q '[:find ?f
         :where
         [?e :db/ident ?f]
         [(namespace ?f) ?name]
         [(.startsWith ^String ?name "field")]] (d/db conn))))

(defn add-catalog [name]
  (let [conn (d/connect uri)]
    @(d/transact conn [{:db/id #db/id[:db.part/user] :catalog/name name}])))

(defn add-field [name]
  (let [conn (d/connect uri)]
    @(d/transact conn [{:db/id #db/id[:db.part/db]
                        :db/ident (str "field/" name)
                        :db/valueType :db.type/string
                        :db/cardinality :db.cardinality/one
                        :db/doc (str "A part's " name)
                        :db.install/_attribute :db.part/db}])))
