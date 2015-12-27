(ns om-next-ideas.datomic
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.java.io :as io]
    [schema.core :as s]
    [com.stuartsierra.component :as component]
    [datomic.api :as d])
  (:import datomic.Util))

(defrecord Datomic [uri schema]
  component/Lifecycle
  (start [component]
    (d/delete-database uri)
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema)
      (assoc component :connection conn)))
  (stop [component]
    (dissoc component :connection)))

(defn new-datomic-db [uri]
  (map->Datomic {:uri    uri
                 :schema (first (Util/readAll (io/reader (io/resource "data/schema.edn"))))}))


