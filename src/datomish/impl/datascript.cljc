(ns datomish.impl.datascript
  (:require [datomish.api :as d]
            [datascript.core :as ds]
            [datomish.impl.datascript-wraps :as w]))

(def wrap-dynamic w/wrap-dynamic)

(d/extend-db-type
 #?(:clj          datascript.db.DB
    :cljs datascript.db/DB)
 {:pull      ds/pull
  :datoms    ds/datoms
  :pull-many ds/pull-many
  :q         ds/q
  :transact  ds/transact!
  :entity    ds/entity
  :listen    ds/listen!
  :entid     ds/entid
  :unlisten  ds/unlisten!
  :schema    #(ds/schema @%)
  :with      ds/with})

;; TODO CLJS Intern?
#?(:clj
   (doseq [[k v] (dissoc (ns-interns `datascript.core) 'conn-from-db 'create-conn)]
     (intern *ns* k v)))

(defn create-conn
  ([] (w/wrap-dynamic (ds/create-conn)))
  ([schema] (w/wrap-dynamic (ds/create-conn schema))))

(def transact d/transact)
(def conn-from-db ds/conn-from-db)
