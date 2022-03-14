(ns datomish.impl.datalevin
  (:require [datalevin.core :as dl]
            [datomish.api :as d]))

(d/extend-db-type
 datalevin.db.DB
 {:pull      dl/pull
  :pull-many dl/pull-many
  :q         dl/q
  :transact  dl/transact
  :transact! dl/transact!
  :entity    dl/entity
  :listen    dl/listen!
  :datoms    dl/datoms
  :unlisten  dl/unlisten!
  :schema    dl/schema
  :with      dl/with  })
