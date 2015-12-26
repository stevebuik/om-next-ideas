(ns om-next-ideas.system
  (:require
    [clojure.pprint :refer [pprint]]
    [schema.core :as s]
    [com.stuartsierra.component :as component]
    [om-next-ideas.server :as server]
    [om-next-ideas.remote-parser :as parser]
    [om-next-ideas.datomic :as datomic]))

(s/defn system
  []
  (component/system-map
    :datomic (datomic/new-datomic-db "datomic:mem://localhost:4334/ideas")
    :parser-api parser/new-api
    :server (server/new-server)))

(defn start
  "Performs side effects to initialize the system, acquire resources,
  and start it running. Returns an updated instance of the system."
  [system]
  (component/start system))

(defn stop
  "Performs side effects to shut down the system and release its
  resources. Returns an updated instance of the system."
  [system]
  (component/stop system))

(comment
  (def system' (system))
  (start system'))