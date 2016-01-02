(ns om-next-ideas.app.mutation-controller
  (:require
    #?(:clj [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])
            [schema.core :as s]
            [om-next-ideas.parsing-utils :as pu]
            [om-next-ideas.core :refer [OmIdent]]))

; the mutation message translation layer for the client/app parser

(defn type= [t]
  (fn [v] (= t (:type v))))

(s/defschema Message
  (s/conditional
    (type= :app/add-car) {:type (s/eq :app/add-car)
                          :name s/Str}
    (type= :app/add-person) {:type (s/eq :app/add-person)
                             :name s/Str}
    (type= :app/edit-person) {:type                  (s/eq :app/edit-person)
                              :id                    OmIdent
                              (s/optional-key :name) s/Str
                              (s/optional-key :cars) [OmIdent]}
    (type= :app/edit-complete) {:type (s/eq :app/edit-complete)
                                :id   OmIdent}))

(s/defn dirty?
  [state ident]
  (contains? (get-in state [:ui :dirty]) ident))

(s/defn ^:always-validate message->mutation
  "HOF closing over app state to allow state aware mutation generation"
  [state-atom]
  (s/fn [msg :- Message]
    (case (:type msg)

      :app/add-car (let [{:keys [name]} msg
                         params {:temp-id  (pu/temp-id :car/by-id)
                                 :car/name name}]
                     `[(app/add-car ~params)])

      :app/add-person (let [{:keys [name]} msg
                            params {:temp-id     (pu/temp-id :person/by-id)
                                    :person/name name}]
                        `[(app/add-person ~params)])

      :app/edit-person (let [{:keys [id name cars]} msg
                             params (cond-> {:db/id (last id)}
                                            name (assoc :person/name name)
                                            cars (assoc :person/cars (map last cars)))]
                         `[(app/save-person ~params)])

      :app/edit-complete (let [{:keys [id]} msg
                               params {:ident id}]
                           ; use dirty check to avoid mutations when app first loads and
                           ; each text input generates a blur event as the next one grabs focus
                           (when (dirty? @state-atom id)
                             `[(app/sync-person ~params)])))))
