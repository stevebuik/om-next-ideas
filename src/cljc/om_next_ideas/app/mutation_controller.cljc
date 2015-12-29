(ns om-next-ideas.app.mutation-controller
  (:require
    [schema.core :as s]
    [om-next-ideas.core :refer [OmIdent]]))

; the mutation message translation layer for the client/app parser

(defn type= [t]
  (fn [v] (= t (:type v))))

(s/defschema Message
  (s/conditional
    (type= :app/add-person) {:type (s/eq :app/add-person)
                             :name s/Str}
    (type= :app/edit-person) {:type                  (s/eq :app/edit-person)
                              :id                    OmIdent
                              (s/optional-key :name) s/Str
                              (s/optional-key :cars) [OmIdent]}
    (type= :app/edit-complete) {:type (s/eq :app/edit-complete)
                                :id   OmIdent}))

(s/defn message->mutation
  [msg :- Message]
  (case (:type msg)

    :app/add-person (let [{:keys [name]} msg
                          params {:person/name name}]
                      `[(app/add-person ~params)
                        #_{:people [:db/id :person/name]}])

    :app/edit-person (let [{:keys [id name cars]} msg
                           params (cond-> {:db/id (last id)}
                                          name (assoc :person/name name)
                                          cars (assoc :person/cars (map last cars)))]
                       `[(app/save-person ~params)
                         #_{:people [:db/id :person/name]}])

    :app/edit-complete (let [{:keys [id]} msg
                             params {:db/id (last id)}]
                         `[(app/sync-person ~params)])

    ))
