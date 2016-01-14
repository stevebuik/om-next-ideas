(ns om-next-ideas.app.parsing
  (:require
    #?(:clj [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])
            [schema.core :as s]
            [om-next-ideas.core :refer [Id OmIdent]]
            [om-next-ideas.parsing-utils :as pu :refer [readf mutate]]
            [taoensso.timbre :as log]
            [clojure.walk :as wlk]))

; SCHEMAS

(s/defschema EngineQuery [(s/enum :db/id :engine/torque :engine/hp)])

(s/defschema CarQuery [(s/conditional
                         ; TODO if :car/selected is included then :db/id must also be present
                         keyword? (s/enum :db/id :car/name :car/selected)
                         map? {:car/engine EngineQuery})])

(s/defschema PersonQuery [(s/conditional
                            keyword? (s/enum :db/id :person/name)
                            map? {:person/cars CarQuery})])

; READ

(s/defn sanitize
  "remove any client-only keys from an ast so that it can be sent to the remote"
  [query]
  (wlk/postwalk (fn [n]
                  (cond
                    (and (vector? n)
                         (not= :key (first n))
                         (not= :dispatch-key (first n))) (vec (remove #{:car/selected} n))
                    :else n))
                query))

(s/defmethod readf :people-edit
             [{:keys [state query target ast] :as env} :- (pu/env-with-query PersonQuery)
              _ _]
             (let [people-absent? (nil? (:people @state))]
               (case target
                 nil {:value (pu/parse-join-multiple env query :person (:people @state))}
                 :remote (when people-absent? {:remote (-> ast
                                                           (assoc :dispatch-key :people
                                                                  :key :people)
                                                           sanitize)}))))

(s/defmethod readf :people-display
             [{:keys [state query target ast] :as env} :- (pu/env-with-query PersonQuery)
              _ _]
             {:value (pu/parse-join-multiple env query :person (:people @state))})

(s/defmethod readf :person
             [{:keys [state query] :as env} :- (pu/env-with-query PersonQuery)
              _
              params]
             (let [person (pu/get-linked env params :person/by-id :person)
                   cars-join (pu/get-sub-query env :person/cars)]
               (log/trace :read-person {:person person
                                        :cj     cars-join
                                        :cars   (:cars @state)})
               {:value (-> person                           ; denormalized record with all fields
                           (select-keys (filterv keyword? query)) ; only return fields listed in the query
                           (cond->                          ; apply any joins

                             ; cars join for person means all known cars with a :car/selected flag
                             ; supported if included in the query
                             cars-join (assoc :person/cars
                                              (let [all-cars (:cars @state)
                                                    car-ids-owned (->> person :person/cars (map last) set)
                                                    selected-in-query (contains? (set cars-join) :car/selected)]
                                                (cond->>
                                                  (pu/parse-join-multiple (assoc env :parent-type :person)
                                                                          cars-join
                                                                          :car all-cars)
                                                  selected-in-query (mapv #(assoc % :car/selected
                                                                                    (contains? car-ids-owned (:db/id %)))))))))}))

(defn- is-selected-allowed?
  [{:keys [query parent-type]}]
  (let [selected-in-query (contains? (set query) :car/selected)]
    (or (and selected-in-query (= :person parent-type))
        (and (not selected-in-query) (nil? parent-type)))))

(defn- validate-car-query!
  [env]
  (assert (is-selected-allowed? env) ":car/selected only allowed inside a :person join query"))

(s/defmethod readf :cars
             [{:keys [state query target ast parent-type] :as env} :- (pu/env-with-query CarQuery)
              _
              params]
             (validate-car-query! env)
             (let [all-cars (:cars @state)]
               (case target
                 nil {:value (pu/parse-join-multiple env query :car all-cars)}
                 :remote (when (nil? all-cars) {:remote ast}))))

(s/defmethod readf :car
             [{:keys [state query] :as env} :- (pu/env-with-query CarQuery)
              _
              params]
             (validate-car-query! env)
             (let [car (pu/get-linked env params :car/by-id :car)
                   engine-join (pu/get-sub-query env :car/engine)]
               (log/trace "read car" {:car    car
                                      :query  query
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
             {:value (throw #?(:clj  (RuntimeException.)
                               :cljs (js/Error.)))})

; MUTATION

(s/defn dirty!
  [state
   record-id :- OmIdent]
  (if (get-in state [:ui :dirty])
    (update-in state [:ui :dirty] conj record-id)
    (assoc-in state [:ui :dirty] #{record-id})))

(s/defmethod mutate 'app/error [& args] {:action #(/ 1 0)})

(s/defmethod mutate 'app/add-car
             [{:keys [state]} _
              {:keys [temp-id car/name car/engine]} :- {:temp-id  OmIdent
                                                        :car/name s/Str}]
             {:action (fn []
                        (swap! state #(-> %
                                          (dirty! temp-id)
                                          (assoc-in (concat [:om.next/tables] temp-id)
                                                    {:db/id    (last temp-id)
                                                     :car/name name})
                                          (update-in [:cars] conj temp-id))))})
(s/defmethod mutate 'app/save-car
             [{:keys [state]} _
              {:keys [db/id car/name]} :- {:db/id                     Id
                                           (s/optional-key :car/name) s/Str}]
             {:action (fn []
                        (swap! state (fn [s]
                                       (cond-> (dirty! s [:car/by-id id])
                                               name (assoc-in [:om.next/tables :car/by-id id :car/name]
                                                              name)))))})
(s/defmethod mutate 'app/add-person
             [{:keys [state]} _
              {:keys [temp-id person/name]} :- {:temp-id     OmIdent
                                                :person/name s/Str}]
             {:action (fn []
                        (swap! state #(-> %
                                          (dirty! temp-id)
                                          (assoc-in (concat [:om.next/tables] temp-id) {:db/id       (last temp-id)
                                                                                        :person/name name})
                                          (update-in [:people] conj temp-id))))})

(s/defmethod mutate 'app/save-person
             [{:keys [state]} _
              {:keys [db/id person/name selection]} :- {:db/id                        Id
                                                        (s/optional-key :person/name) s/Str}]
             {:action (fn []
                        (swap! state (fn [s]
                                       (cond-> (dirty! s [:person/by-id id])
                                               name (assoc-in [:om.next/tables :person/by-id id :person/name]
                                                              name)))))})

(s/defmethod mutate 'app/toggle-car
             [{:keys [state target ast]} _
              {:keys [db/id car selected]} :- {:db/id    Id
                                               :car      OmIdent
                                               :selected s/Bool}]
             (case target
               nil {:action #(swap! state update-in [:om.next/tables :person/by-id id :person/cars]
                                    (fn [car-idents]
                                      (vec (if selected
                                             (conj car-idents car)
                                             (remove #{car} car-idents)))))}
               :remote (let [person (-> @state :om.next/tables :person/by-id (get id))
                             car-ids (map last (:person/cars person))]
                         {:remote (-> ast
                                      (merge {:dispatch-key 'app/sync
                                              :key          'app/sync})
                                      (assoc-in [:params] {:db/id       id
                                                           :person/name (:person/name person)
                                                           :person/cars car-ids}))})))

(s/defmethod mutate 'app/sync
             [{:keys [state ast target]} _
              {:keys [ident]} :- {:ident OmIdent}]
             (case target
               nil {:action (fn []
                              ; TODO how to stop this re-rendering all people :
                              ; could be the remote callback causing full re-render, not the optimistic update
                              (swap! state update-in [:ui :dirty] disj ident))}
               ; hydrate the local copy of the person and send it to the remote
               :remote (let [record (-> ident (pu/normalized->tree (:om.next/tables @state)))]
                         {:remote (assoc-in ast [:params] (dissoc record :person/cars))})))

