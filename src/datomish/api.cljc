(ns datomish.api
  "Generic operations for datomic-like database."
  (:require [datascript.core :as ds]))

;; ------------------------------------------------- ;;
;; Generic Operators

(defmulti transact (fn [dbc & rest] (type @dbc)))
(defmulti listen (fn [dbc & rest] (type @dbc)))
(defmulti unlisten (fn [dbc & rest] (type @dbc)))
(defmulti schema (fn [dbc] (type @dbc)))

(defmulti q (fn [query db & rest] (type db)))
;; Where we fail to find the appropriate db-type for a query,
;; we'll treat the 'db' as a list of triplets (i.e. datoms) and use
;; datascript's operator.
;; Converting every datoms to lists is probably
;; not the most efficient method, but it'll make sure datascript's q works
;; (if datom of a particular db-type supports conversion to list).
(defmethod q :default [query datoms & args]
  (apply ds/q (map #(apply list %) datoms)
         args))

(defprotocol DB
  :extend-via-metadata true
  (pull [db pattern eid])
  (pull-many [db pattern eids])
  (entity [db eid])
  (entid  [db eid])
  (datoms [db index])
  (with [db tx-data]))

;; ------------------------------------------------- ;;

(defn extend-db-type
  "Implement the db type.
  Adapter is a map of each operations, as keywods,
  to the actual functions, specifically:
  :pull, :datoms, :pull-many, :q, :transact
  :entity, :listen, :unlisten, :schema, :with.

  An operator can be made extensible via meta of a specific db instance.
  Meta adapters are given first priority."
  [type adapter]

  (defmethod schema type [dbc]
    ((or (-> dbc meta (get `schema))
         (:schema adapter))
     dbc))

  (defmethod transact type [& args]
    (apply
     (or (-> args first meta (get `transact))
         (:transact adapter))
     args))

  (defmethod q type [& args]
    (apply
     (or (-> args first meta (get `q))
         (:q adapter))
     args))

  (defmethod listen type
    ([dbc callback]
     ((or (-> dbc meta (get `listen))
          (:listen adapter))
      dbc callback))
    ([dbc key callback]
     ((or (-> dbc meta (get `listen))
          (:listen adapter))
      dbc key callback)))

  (defmethod unlisten type
    ([dbc key]
     ((or (-> dbc meta (get `unlisten))
          (:unlisten adapter))
      dbc key)))

  ;; No 'extend for cljs.
  ;; And 'extend-type expands immediately in clj,
  ;; which complains about unknown 'type' symbol.

  #?(:cljs
     (extend-type type
       DB
       (pull [db pattern eid]
         ((:pull adapter) db pattern eid))
       (pull-many [db pattern eids]
         ((:pull-many adapter) db pattern eids))
       (entity [db eid]
         ((:entity adapter) db eid))
       (entid [db eid]
         ((:entid adapter) db eid))
       (datoms [db index]
         ((:datoms adapter) db index))
       (with [db tx-data]
         ((:with adapter) db tx-data)))

     :clj
     (extend type
       DB
       {:pull      (fn [db pattern eid]
                     ((:pull adapter) db pattern eid))
        :pull-many (fn [db pattern eids]
                     ((:pull-many adapter) db pattern eids))
        :entity    (fn [db eid]
                     ((:entity adapter) db eid))
        :entid     (fn [db eid]
                     ((:entid adapter) db eid))
        :datoms    (fn [db index]
                     ((:datoms adapter) db index))
        :with      (fn [db tx-data]
                     ((:with adapter) db tx-data))})))
