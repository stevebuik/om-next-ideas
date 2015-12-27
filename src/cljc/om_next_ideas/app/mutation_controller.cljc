(ns om-next-ideas.app.mutation-controller
  (:require [schema.core :as s]))

; the mutation message translation layer for the client/app parser

(s/defschema Message
  (s/conditional
    #(= :app/add-person (:type %)) {:type        (s/eq :app/add-person)
                                    :person/name s/Str}
    ))

(s/defn message->mutation
  [msg :- Message]
  (let [without-type (dissoc msg :type)]
    (case (:type msg)

      :app/add-person `[(app/add-person ~without-type)
                        #_{:people [:db/id :person/name]}]

      )))
