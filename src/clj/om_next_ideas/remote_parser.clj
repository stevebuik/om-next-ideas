(ns om-next-ideas.remote-parser
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.set :as set]
    [om-next-ideas.parsing-utils :as pu]
    [om.next.server :as om]
    [com.stuartsierra.component :as component]
    [schema.core :as s]
    [datomic.api :as d]
    [taoensso.timbre :as log]))

; read fns

(s/defmethod pu/readf :people
             [{:keys [db query]} _ params]
             (let [q '[:find [(pull ?p selector) ...]
                       :in $ selector
                       :where
                       [?p :person/name]]]
               (log/trace {:query query
                           :q     q})
               (->> query
                    (d/q q db)
                    (hash-map :value))))

; mutations

(s/defschema MutationEnv {:db         s/Any
                          :connection s/Any
                          s/Keyword   s/Any})

(s/defmethod ^:always-validate pu/mutate 'app/add-person
             [{:keys [db connection]} :- MutationEnv
              _
              params :- {:person/name s/Str}]
             {:action (fn []
                        (let [p (assoc
                                  (select-keys params [:person/name])
                                  :db/id (d/tempid :db.part/user))]
                          (:tempids @(d/transact connection [p]))))})

; Component and API Protocol

(defprotocol Parser
  (parse [this request]))

(defrecord ParserImpl [parser]
  Parser
  (parse [c request]
    (parser (assoc c
              ; add the database value at the start of the request as a convenience for read fns
              :db (d/db (get-in c [:datomic :connection]))
              :connection (get-in c [:datomic :connection]))
            request))

  component/Lifecycle
  (start [component]
    (assoc component :parser parser))
  (stop [component]
    (dissoc component :parser)))

(defn new-api []
  (component/using
    ; TODO use a wrapper here to log exceptions and return an error result to client without the stack trace
    (->ParserImpl (om/parser {:read pu/readf :mutate pu/mutate}))
    [:datomic]))