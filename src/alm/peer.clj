(ns alm.peer
  (:require [datomic.api :as d :refer (q)]))

(def uri "datomic:mem://alm")

(defn schema-tx []  (read-string (slurp "resources/alm/schema.edn")))

(defn init-db []
  (when (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn (schema-tx)))))

(defn update-db []
   (let [conn (d/connect uri)]
      @(d/transact conn (schema-tx))))

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
         [(.startsWith ^String ?name "")]] (d/db conn))))

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

(defn relate-fields [catalog-name field-ids]
  (let [conn (d/connect uri)
        catalog-id (first (first (q `[:find ?c :where [?c :catalog/name ~catalog-name]] (d/db conn))))]
    @(d/transact conn `[{:db/id ~catalog-id :catalog/fields ~field-ids}])))

(defn get-all-catalogs-with-fields []
  (let [conn (d/connect uri)]
    (q '[:find ?n (vec ?fs) :where [?e :catalog/name ?n] [?e :catalog/fields ?fs]] (d/db conn))))

(defn get-fields-for-catalog [catalog-name]
  (let [conn (d/connect uri)]
    (q `[:find ?fs :where [?e :catalog/name ~catalog-name] [?e :catalog/fields ?fs]] (d/db conn))))
