(ns om-next-ideas.parsing-integration-tests
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.string :as string]
    [clojure.test :refer [testing is are]]
    [com.stuartsierra.component :as component]
    [om-next-ideas.system :as system]
    [om-next-ideas.remote-parser :as remote-parser]
    [om-next-ideas.app.mutation-controller :as controller]
    [schema.core :as s]
    [taoensso.timbre :as log]))

; tests that run the client parser on top of the server parser using client messages
; i.e. simulate the entire stack without a UI

(log/with-merged-config
  {:output-fn (fn [{:keys [level ?ns-str msg_] :as d}]
                (str (string/upper-case (name level)) " " ?ns-str " >> " (force msg_)))}
  (s/with-fn-validation
    (let [{:keys [parser-api] :as sys} (component/start (system/system {}))
          read-remote (fn [q] (remote-parser/parse parser-api q))
          mutate-remote! (fn [msg]
                           (->> msg
                                controller/message->mutation
                                (remote-parser/parse parser-api)))]

      ; local: initial query returns no data
      ; local: initial query returns same for :remote
      ; remote: initial query returns example
      ; local: initial query refresh returns same

      ; local: add-person mutation success
      ; local: mutation query(s) returns example (optimistic insert)
      ; local: mutation returns same for :remote
      ; remote: mutation success returns success + query(s) + tempids

      ; local: mutation results merged
      ; local: mutation query(s) returns example (server refresh)

      (is (zero? (->> [{:people [:db/id]}]
                      read-remote
                      :people count))
          "No people initially")

      (mutate-remote! {:type        :app/add-person
                       :person/name "Clark Kent"})
      (mutate-remote! {:type        :app/add-person
                       :person/name "Bruce Wayne"})

      (is (= (read-remote [{:people [:person/name]}])
             {:people [{:person/name "Clark Kent"}
                       {:person/name "Bruce Wayne"}]})
          "Both records returned by remote after mutation")

      )))

