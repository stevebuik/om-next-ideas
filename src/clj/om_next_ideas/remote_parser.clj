(ns om-next-ideas.remote-parser
  (:require
    [om-next-ideas.parsing-utils :as pu]
    [om.next.server :as om]
    [com.stuartsierra.component :as component]))

(defn handle-api-request
  [{:keys [parser]} req]
  (let [{:keys [datomic]} parser]
    (parser req)))

(defrecord Parser [parser]
  component/Lifecycle
  (start [component]
    (assoc component :parser parser))
  (stop [component]
    (dissoc component :parser)))

(defn new-api []
  (component/using (->Parser (om/parser {:read pu/readf :mutate pu/mutate}))
                   [:datomic]))