(ns om-next-ideas.remote-parser
  (:require
    [clojure.pprint :refer [pprint]]
    [om-next-ideas.remote-core :refer [readf mutate Id OmIdent]]
    [om.next.server :as om]
    [com.stuartsierra.component :as component]
    [schema.core :as s]
    [datomic.api :as d]
    [taoensso.timbre :as log])
  (:import (om.tempid TempId)))

; read fns

(s/defmethod readf :people
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

(s/defmethod ^:always-validate mutate 'app/sync-person
             [{:keys [db connection]} :- MutationEnv
              _
              ; TODO NEXT change :db/id to be OmIdent and remove temp-id
              ; since that can be derived from the type of :db/id
              ; and :tempids can be conditionally returned based on that
              ; i.e. sync-person can become sync-anything with schema to
              ; control what can be sent
              {:keys [db/id] :as params} :- {:db/id       Id
                                             :person/name s/Str}]
             {:action (fn []
                        ; TODO protect against incorrect ids from the client i.e.
                        ; when not a tempid, check existing
                        ; attributes to ensure a matching entity type in the db

                        (let [is-update? (number? id)
                              db-id (if is-update? id (d/tempid :db.part/user))
                              p (assoc
                                  (select-keys params [:person/name])
                                  :db/id db-id)
                              {:keys [db-after tempids]} @(d/transact connection [p])]
                          (when (not is-update?)
                            {:tempids {id (d/resolve-tempid db-after tempids db-id)}})))})

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
    (->ParserImpl (om/parser {:read readf :mutate mutate}))
    [:datomic]))