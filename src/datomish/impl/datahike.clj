(ns datomish.impl.datahike
  (:require [datomish.api :as d]
            [datahike.api :as dh]))

(d/extend-db-type
 datahike.db.HistoricalDB
 {:q dh/q})

(d/extend-db-type
 datahike.db.DB
 {:pull      dh/pull
  :datoms    dh/datoms
  :pull-many dh/pull-many
  :q         dh/q
  :transact  dh/transact
  :transact! (comp deref dh/transact)
  :entity    dh/entity
  :listen    dh/listen})

(doseq [[k v] (ns-interns `datahike.api)]
  (intern *ns* k v))
