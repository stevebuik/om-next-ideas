(ns om-next-ideas.datomic
  (:require
    [clojure.pprint :refer [pprint]]
    [schema.core :as s]
    [com.stuartsierra.component :as component]
    [datomic.api :as d]))

(defrecord Datomic [uri]
  component/Lifecycle
  (start [component]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      (assoc component :connection conn)))
  (stop [component]
    (dissoc component :connection)))

(defn new-datomic-db [uri]
  (map->Datomic {:uri uri}))


