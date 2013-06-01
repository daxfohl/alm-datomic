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
    (q '[:find ?e ?c :where [?e :catalog/name ?c]] (d/db conn))))

(defn get-fields []
  (let [conn (d/connect uri)]
    (q all-fields-query (d/db conn))))

(def all-fields-query
  '[:find ?e ?name 
      :where
      [?e :db/ident ?name]
      [(namespace ?name) ?ns]
      [(= ?ns "field")]])

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

(defn add-part [catalog-id fields]
  (let [conn (d/connect uri)]
    @(d/transact conn [(assoc fields
                         :db/id #db/id[:db.part/user]
                         :part/catalog catalog-id)])))

(defn get-parts [catalog-id]
  (let [conn (d/connect uri)
        db (d/db conn)
        eid-results (q `[:find ?e :where [?e :part/catalog ~catalog-id]] (d/db conn))
        eids (map first eid-results)
        entities (map #(d/entity db %1) eids)
        values (map seq entities)]
    values))

(defn relate-fields [catalog-id field-ids]
  (let [conn (d/connect uri)]
    @(d/transact conn `[[:assertWithRetracts ~catalog-id :catalog/fields ~field-ids]])))

(defn get-all-catalogs-with-fields []
  (let [conn (d/connect uri)]
    (q  '[:find ?e ?n (vec ?result)
          :where
          [?e :catalog/name ?n]
          [?e :catalog/fields ?field]
          [?field :db/ident ?fieldname]
          [(vector ?field ?fieldname) ?result]] (d/db conn))))

(def get-catalog-with-fields-query
  '[:find ?n (vec ?result)
    :in $ ?e
    :where
    [?e :catalog/name ?n]
    [?e :catalog/fields ?field]
    [?field :db/ident ?fieldname]
    [(vector ?field ?fieldname) ?result]])

(defn get-catalog-with-fields [catalog-id]
  (let [conn (d/connect uri)]
    (first (q get-catalog-with-fields-query (d/db conn) catalog-id))))

(defn get-catalog-and-all-fields [catalog-id]
  (let [conn (d/connect uri)
        db (d/db conn)]
    [(first (q get-catalog-with-fields-query db catalog-id)) (q all-fields-query db)]))

