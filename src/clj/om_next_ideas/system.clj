(ns om-next-ideas.system
  (:require
    [clojure.pprint :refer [pprint]]
    [schema.core :as s]
    [com.stuartsierra.component :as component]
    [om-next-ideas.server :as server]
    [om-next-ideas.figwheel-component :as fw]
    [om-next-ideas.remote-parser :as parser]
    [om-next-ideas.datomic :as datomic]))

(s/defn ^:always-validate system
  [db-uri :- s/Str
   extras :- {(s/optional-key :server)   (s/protocol component/Lifecycle)
              (s/optional-key :figwheel) (s/protocol component/Lifecycle)}]
  (cond-> (component/system-map
            :datomic (datomic/new-datomic-db db-uri)
            :parser-api (parser/new-api)
            :routes (server/new-routes))
          extras (merge extras)))

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

(def local-app-db "datomic:mem:/ideas")

(comment
  ; start normal server on 8080
  (def system' (system local-app-db {:server (server/new-server)}))

  ; or figwheel on 3449
  (def system' (system local-app-db {:figwheel (fw/figwheel)}))

  ; then
  (start system'))