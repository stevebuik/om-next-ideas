(ns om-next-ideas.app.parsing
  (:require
    #?(:clj [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])
            [om-next-ideas.core :as c :refer [readf mutate OmIdent]]
            [schema.core :as s]))

; SCHEMAS

(s/defschema EngineQuery [(s/enum :engine/id :engine/torque :engine/hp)])

(s/defschema CarQuery [(s/either
                         (s/enum :car/id :car/name)
                         {:car/engine EngineQuery})])

(s/defschema PersonQuery [(s/either
                            (s/enum :person/id :person/name)
                            {:person/cars CarQuery})])

; READ

(s/defmethod readf :people
             [{:keys [state query] :as env} :- (c/env-with-query PersonQuery)
              _ _]
             {:value (c/parse-join-multiple env query :person (:people @state))})

(s/defmethod readf :person
             [{:keys [state query] :as env} :- (c/env-with-query PersonQuery)
              _
              params]
             (let [person (c/get-linked env params :person/id :person)
                   cars-join (c/get-sub-query env :person/cars)]
               {:value (cond-> (select-keys person (filterv keyword? query))
                               cars-join (c/merge-join-multiple env cars-join :car :person/cars person))}))

(s/defmethod readf :car
             [{:keys [state query] :as env} :- (c/env-with-query CarQuery)
              _
              params]
             (let [car (c/get-linked env params :car/id :car)
                   engine-join (c/get-sub-query env :car/engine)]
               {:value (cond-> (select-keys car query)
                               engine-join (c/merge-join-single env engine-join :engine :car/engine car))}))

(s/defmethod readf :engine
             [{:keys [state query] :as env} :- (c/env-with-query EngineQuery)
              _
              params]
             {:value (select-keys (c/get-linked env params :engine/id :engine) query)})

