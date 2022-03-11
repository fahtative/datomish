(ns datomish.api
  "A generic operations for datomic-like database."
  (:require [datascript.core :as ds]
            [clojure.walk :refer [prewalk]]))

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
  to the actual functions.
  Specifically:
  :pull, :datoms, :pull-many, :q, :transact
  :entity, :listen, :unlisten, :schema, :with."
  [type adapter]

  ;; Since we know the exact positons of conn or db,
  ;; we could lookup an adapter function from meta.
  ;; So an operator can be made extensible via meta.
  ;; We give meta adapters first priority.
  (defmethod schema type [dbc]
    (or ((-> dbc meta (get `schema)) dbc)
        ((:schema adapter) dbc)))

  (defmethod transact type [& args]
    (apply
     (or (-> args first meta (get `transact))
         (:transact adapter))
     args))

  (defmethod q type [& args]
    (apply
     (or (-> args first meta (get `transact))
         (:q adapter) args)
     args))

  (defmethod listen type
    ([dbc callback]
     (if-let [f (-> dbc meta (get `listen))]
       (f dbc callback)
       ((:listen adapter) dbc callback)))
    ([dbc key callback]
     (if-let [f (-> dbc meta (get `listen))]
       (f dbc key callback)
       ((:listen adapter) dbc key callback))))

  (defmethod unlisten type
    ([conn key] ((:unlisten adapter) conn key)))

  ;; No 'extend for cljs.
  ;; And 'extend-type expand immediately in clj,
  ;; rather waiting for 'type' value.

  #?(:cljs
     (extend-type type
       DB
       (pull [db pattern eid]
         ((or (-> db meta (get `pull))
              (:pull adapter))
          db pattern eid))
       (pull-many [db pattern eids]
         ((or (-> db meta (get `pull-many))
              (:pull-many adapter))
          db pattern eids))
       (entity [db eid]
         ((or (-> db meta (get `entity))
              (:entity adapter))
          db eid))
       (entid [db eid]
         ((or (-> db meta (get `entid))
              (:entid adapter))
          db eid))
       (datoms [db index]
         ((or (-> db meta `datoms)
              (:db adapter))
          db index))
       (with [db tx-data]
         ((or (-> db meta `with)
              (:with adapter))
          db tx-data)))

     :clj
     (extend type
       DB
       {:pull      (fn [db pattern eid]
                     ((or (-> db meta (get `pull))
                          (:pull adapter))
                      db pattern eid))
        :pull-many (fn [db pattern eids]
                     ((or (-> db meta (get `pull-many))
                          (:pull-many adapter))
                      db pattern eids))
        :entity    (fn [db eid]
                     ((or (-> db meta (get `entity))
                          (:entity adapter))
                      db eid))
        :entid     (fn [db eid]
                     ((or (-> db meta (get `entid))
                          (:entid adapter))
                      db eid))
        :datoms    (fn [db index]
                     ((or (-> db meta (get `datoms))
                          (:db adapter))
                      db index))
        :with      (fn [db tx-data]
                     ((or (-> db meta (get `with))
                          (:with adapter))
                      db tx-data))})))
