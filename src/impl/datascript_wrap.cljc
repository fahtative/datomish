(ns datomish.impl.datascript-wraps
  "Wrap a datascript database, allowing schema transactions/updates,
  as opposed to only creation-time schema which is the default.

  Implementation credit:
  https://github.com/fmnasution/datascript-schema"
  (:require [datascript.core :as ds]))

(defn update-schema-map!
  [conn f & args]
  (swap! conn
         (fn [db f args]
           (with-meta
             (d/init-db
              (or (d/datoms db :eavt) [])
              (apply f (:schema db) args))
             (meta db)))
         f
         args))

(def ^:private ref-attrs
  [:db/unique :db/valueType :db/cardinality])

(def ^:private constrained-schema
  {:db/unique #{:db.unique/value
                :db.unique/identity}
   :db/valueType #{:db.type/ref}
   :db/cardinality #{:db.cardinality/one
                     :db.cardinality/many}})

(def ^:private schema-pattern
  ['*
   (into {}
         (map (fn [ref-attr]
                [ref-attr ['*]]))
         ref-attrs)])

(defn- schema->schema-map
  [{attr :db/ident :as schema} option]
  (when-let [m (transduce
                (keep (fn [[k v]]
                        (let [constrainer (get option k)
                              new-v (cond-> v
                                      (map? v) (:db/ident)
                                      constrainer constrainer)]
                          (when new-v
                            [k new-v]))))
                merge
                (dissoc schema :db/id :db/ident))]
    {attr m}))

(defn- check-schema-change
  [{:keys [db-before db-after tx-data]}]
  (let [get-eids (fn [db tx-data]
                   (d/q '{:find [[?eid ...]]
                          :in [$ [[?eid]]]
                          :where [[?eid :db/ident]]}
                        db
                        tx-data))
        updating-eids (get-eids db-after tx-data)
        deleting-eids (into []
                            (remove (set updating-eids))
                            (get-eids db-before tx-data))
        schema-map (when (seq updating-eids)
                     (->> updating-eids
                          (d/pull-many db-after schema-pattern)
                          (transduce (keep #(schema->schema-map
                                             %
                                             constrained-schema))
                                     merge)))
        schema-keys (when (seq deleting-eids)
                      (d/q '{:find [[?attr ...]]
                             :in [$ [?eid ...]]
                             :where [[?eid :db/ident ?attr]]}
                           db-before
                           deleting-eids))]
    {:schema-map schema-map
     :schema-keys schema-keys}))

(defn- apply-delta-schema!
  [conn tx-report]
  (let [{:keys [schema-map schema-keys]} (check-schema-change tx-report)]
    (when (seq schema-map)
      (update-schema-map! conn merge schema-map))
    (when (seq schema-keys)
      (apply update-schema-map! conn dissoc schema-keys))))

(def ^:private initial-schema
  {:db/ident {:db/unique :db.unique/identity}
   :db/valueType {:db/valueType :db.type/ref}
   :db/unique {:db/valueType :db.type/ref}
   :db/cardinality {:db/valueType :db.type/ref}})

(def ^:private initial-tx-data
  [{:db/id -1
    :db/ident :db.type/ref}
   {:db/id -2
    :db/ident :db.unique/identity}
   {:db/id -3
    :db/ident :db.unique/value}
   {:db/id -4
    :db/ident :db.cardinality/one}
   {:db/id -5
    :db/ident :db.cardinality/many}
   {:db/id -6
    :db/ident :db/ident
    :db/unique -2}
   {:db/id -7
    :db/ident :db/valueType
    :db/valueType -1}
   {:db/id -8
    :db/ident :db/unique
    :db/valueType -1}
   {:db/id -9
    :db/ident :db/cardinality
    :db/valueType -1}
   {:db/id -10
    :db/ident :db/tupleAttrs}])

(defn- all-idents [db]
  (->> db
       (ds/q
        '[:find [?id ...]
          :in $
          :where [?e :db/ident ?id]])
       set))

(defn- non-existing-idents [db tx-data]
  (let [idents (all-idents db)]
    (filter
     (fn [{:keys [db/ident]}]
       (not (get idents ident)))
     tx-data)))

(defn listen-on-schema-change!
  ([conn handler]
   (let [callback (fn [tx-report]
                    (apply-delta-schema! conn tx-report)
                    (handler tx-report))]
     (update-schema-map! conn merge initial-schema)
     (d/transact conn (non-existing-idents @conn initial-tx-data))
     ;; (d/transact conn initial-tx-data)
     (d/listen! conn ::schema callback)
     conn
     ;; ::bootstrapped
     ))
  ([conn]
   (listen-on-schema-change! conn (constantly nil))
   conn))

(defn dynamic-schema! [conn] (listen-on-schema-change! conn))

(defn unlisten-schema-change!
  [conn]
  (d/unlisten! conn ::schema))

(def wrap-dynamic listen-on-schema-change!)
