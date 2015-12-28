(ns om-next-ideas.app.parsing
  (:require
    #?(:clj [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])
            [schema.core :as s]
            [om-next-ideas.core :refer [Id]]
            [om-next-ideas.parsing-utils :as pu :refer [readf mutate]]
            [taoensso.timbre :as log]))

; SCHEMAS

(s/defschema EngineQuery [(s/enum :db/id :engine/torque :engine/hp)])

(s/defschema CarQuery [(s/either
                         (s/enum :db/id :car/name)
                         {:car/engine EngineQuery})])

(s/defschema PersonQuery [(s/either
                            (s/enum :db/id :person/name)
                            {:person/cars CarQuery})])

; READ

(s/defmethod readf :people
             [{:keys [state query] :as env} :- (pu/env-with-query PersonQuery)
              _ _]
             {:value (pu/parse-join-multiple env query :person (:people @state))})

(s/defmethod readf :person
             [{:keys [state query] :as env} :- (pu/env-with-query PersonQuery)
              _
              params]
             (let [person (pu/get-linked env params :person/by-id :person)
                   cars-join (pu/get-sub-query env :person/cars)]
               (log/trace "read person" {:person person
                                         :cj     cars-join})
               {:value (cond-> (select-keys person (filterv keyword? query))
                               cars-join (pu/merge-join-multiple env cars-join :car :person/cars person))}))

(s/defmethod readf :cars
             [{:keys [state query] :as env} :- (pu/env-with-query CarQuery)
              _
              params]
             (let [car-idents (->> @state :om.next/tables :car/by-id keys (map #(vector :car/id %)))
                   value (pu/parse-join-multiple env query :car car-idents)]
               (log/trace "read cars" {:idents car-idents
                                       :params params})
               {:value value}))

(s/defmethod readf :car
             [{:keys [state query] :as env} :- (pu/env-with-query CarQuery)
              _
              params]
             (let [car (pu/get-linked env params :car/by-id :car)
                   engine-join (pu/get-sub-query env :car/engine)]
               (log/trace "read car" {:car    car
                                      :ej     engine-join
                                      :params params})
               {:value (cond-> (select-keys car query)
                               engine-join (pu/merge-join-single env engine-join :engine :car/engine car))}))

(s/defmethod readf :engine
             [{:keys [state query] :as env} :- (pu/env-with-query EngineQuery)
              _
              params]
             {:value (select-keys (pu/get-linked env params :engine/by-id :engine) query)})

(s/defmethod readf :error
             [& args]
             {:value (throw (RuntimeException.))})

; MUTATION

(s/defmethod mutate 'app/error [& args] {:action #(/ 1 0)})

(s/defmethod mutate 'app/add-car
             [{:keys [state]} _
              {:keys [car/name car/engine]}]
             {:action (fn []
                        (swap! state #(let [[_ car-id] (pu/temp-id :car/by-id)]
                                       (-> %
                                           (assoc-in [:om.next/tables :car/by-id car-id]
                                                     {:db/id    car-id
                                                      :car/name name})))))})

(s/defmethod mutate 'app/save-person
             [{:keys [state]} _
              {:keys [db/id person/cars]} :- {:db/id                        Id
                                              (s/optional-key :person/cars) [Id]}]
             {:action (fn []
                        (swap! state (fn [s]
                                       (cond-> s
                                               cars (assoc-in [:om.next/tables :person/by-id id :person/cars]
                                                              (mapv #(vector :car/by-id %) cars))))))})

