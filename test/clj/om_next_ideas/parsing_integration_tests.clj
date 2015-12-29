(ns om-next-ideas.parsing-integration-tests
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer [deftest testing is are]]
    [com.stuartsierra.component :as component]
    [schema.core :as s]
    [taoensso.timbre :as log]

    [om-next-ideas.test-utils :as tu]
    [om-next-ideas.system :as system]

    ; client/local
    [om-next-ideas.core :refer [merge-result-tree]]
    [om-next-ideas.parsing-utils :as pu]
    [om-next-ideas.app.mutation-controller :as controller]
    [om-next-ideas.app.parsing]

    ; server/remote
    [om-next-ideas.remote-parser :as remote-parser]
    [om.next.impl.parser :as p]))

; tests that run the client parser on top of the server parser using client messages
; i.e. simulate the entire stack without a UI

(log/with-merged-config
  tu/log-config
  (log/log-and-rethrow-errors
    (s/with-fn-validation

      ; start the remote api without a web server
      (let [{:keys [parser-api] :as sys} (component/start (system/system "datomic:mem:/test" {}))
            local-state (atom {})                           ; init the client state db
            ; define read and mutation fns for client and server
            read-local (partial pu/wrapped-local-parse {:state local-state})
            read-remote (fn [q] (remote-parser/parse parser-api q))
            parse-local (fn [target req]
                          (pu/wrapped-local-parse {:state local-state} req target))
            mutate-local! (fn [msg target]
                            (->> msg
                                 controller/message->mutation
                                 ((partial parse-local target))))
            mutate-remote! (partial remote-parser/parse parser-api)
            ; init the root query TODO derive this from portable view components
            root-query [{:people [:db/id]}]]

        ; reconciler reads local state to perform first render
        (is (= (read-local root-query nil)
               {:people []})
            "local read returns no people")

        ; reconciler runs root query passing :remote target to determine if a remote send is required
        (let [remote-query (read-local root-query :remote)]
          (is (= remote-query root-query) "root query is sent to remote after initial render")

          ; send fn does remote read using root query
          (let [remote-response (read-remote remote-query)]
            (is (= remote-response {:people []}) "remote db has no people either")

            ; reconciler merges remote response in send callback
            (swap! local-state merge-result-tree remote-response)))

        ; reconciler re-runs root-query after state changed in remote callback processing
        (is (= (read-local root-query nil)
               {:people []})
            "local read after remote response still returns no people because server db is empty")

        ; reconciler re-runs runs root-query again to see if another remote query is required
        (is (= (read-local root-query :remote)
               [])
            "remote read after remote response returns nothing to send")

        ; user emits a message from the ui which is processed locally by the reconciler
        (let [msg-from-ui {:type        :app/add-person
                           :person/name "Clark Kent"}]
          (mutate-local! msg-from-ui nil)

          ; reconciler re-runs queries and will re-render the dependant view components immediately
          (is (= (read-local [{:people [:person/name]}] nil)
                 {:people [{:person/name "Clark Kent"}]})
              "local read returns new person i.e. optimistic update")

          ; reconciler runs :remote mutation to see if a remote call is required
          (let [remote-mutation (mutate-local! msg-from-ui :remote)]

            #_(is (= remote-mutation '[(app/add-person {:person/name "Clark Kent"})])
                  "add person is always sent to server")

            (pprint remote-mutation)

            (pprint (mutate-remote! remote-mutation))

            (pprint @local-state)))


        ; local: add-person mutation success
        ; local: mutation query(s) returns example (optimistic insert)
        ; local: mutation returns same for :remote
        ; remote: mutation success returns success + query(s) + tempids

        ; local: mutation results merged
        ; local: mutation query(s) returns example (server refresh)

        #_#_#_(mutate-remote! {:type        :app/add-person
                               :person/name "Clark Kent"})
            (mutate-remote! {:type        :app/add-person
                             :person/name "Bruce Wayne"})

            (is (= (read-remote [{:people [:person/name]}])
                   {:people [{:person/name "Clark Kent"}
                             {:person/name "Bruce Wayne"}]})
                "Both records returned by remote after mutation")

        ))))

