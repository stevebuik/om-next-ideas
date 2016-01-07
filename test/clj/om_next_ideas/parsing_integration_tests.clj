(ns om-next-ideas.parsing-integration-tests
  (:require
    [clojure.pprint :refer [pprint]]
    [clojure.test :refer [deftest testing is are run-tests]]
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
    [om-next-ideas.server :refer [raise-tempids]]
    [om.next.impl.parser :as p]))

; tests that run the client parser on top of the server parser using client messages
; i.e. simulate the entire stack without a UI

(s/defn get-fixed-tempids
  [mut-key :- s/Symbol
   response]
  (let [{:keys [tempids]} (get response mut-key)]
    (->> tempids
         (map (fn [[k v]] [(pu/ensure-tempid k) v]))
         (into {}))))

(s/defn migrate-insert!
  "process an app/sync response by migrating the tempids and return the migrated id"
  [sync-response local-state temp-id]
  (let [tempids (get-fixed-tempids 'app/sync sync-response)]
    (swap! local-state pu/portable-tempid-migrate nil tempids nil)
    (get tempids (pu/ensure-tempid (pu/ensure-tempid temp-id)))))

(deftest full-stack
  (s/with-fn-validation

    ; start the remote api without a web server
    (let [{:keys [parser-api]} (component/start (system/system "datomic:mem:/test" {}))
          local-state (atom {})                             ; init the client state db
          ; define read and mutation fns for client and server
          read-local (partial pu/wrapped-local-parse {:state local-state})
          read-remote (fn [q] (remote-parser/parse parser-api q))
          parse-local (fn [target req]
                        (pu/wrapped-local-parse {:state local-state} req target))
          mutation-controller (controller/message->mutation local-state)
          mutate-local! (fn [msg target]
                          (some->> msg
                                   mutation-controller
                                   (parse-local target)))
          ; TODO use server wrapper for remote mutation (and read)
          mutate-remote! (fn [req]
                           (->> req
                                (remote-parser/parse parser-api)
                                raise-tempids))
          ; init the root query TODO derive this from portable view components
          root-query [{:people-edit [:db/id :person/name]}
                      {:people-display [:person/name]}
                      {:cars [:db/id :car/name]}]]

      ; reconciler reads local state to perform first render
      (is (= (read-local root-query nil)
             {:people-edit    []
              :people-display []
              :cars           []})
          "local read returns no people")

      ; reconciler runs root query passing :remote target to determine if a remote send is required
      (let [remote-query (read-local root-query :remote)]
        (is (= remote-query [{:people [:db/id :person/name]}
                             {:cars [:db/id :car/name]}])
            "root query is transformed (:people-edit -> :people) and sent to remote after initial render")

        ; send fn does remote read using root query
        (let [remote-response (read-remote remote-query)]
          (is (= remote-response {:people [] :cars []}) "remote db has no people either")

          ; reconciler merges remote response in send callback
          (swap! local-state merge-result-tree remote-response)))

      ; reconciler re-runs root-query after state changed in remote callback processing
      (is (= (read-local root-query nil)
             {:people-edit    []
              :people-display []
              :cars           []})
          "local read after remote response still returns no people because server db is empty")

      ; reconciler re-runs runs root-query again to see if another remote query is required
      (is (= [] (read-local root-query :remote))
          "remote read after remote response returns nothing to send")

      ; user adds a new person with empty name
      (let [msg-from-ui {:type :app/add-person
                         :name ""}]

        (mutate-local! msg-from-ui nil)

        ; reconciler runs :remote mutation to see if a remote call is required
        (is (= (mutate-local! msg-from-ui :remote) []) "add person is not sent to server")

        (let [new-person (read-local [{:people-edit [:db/id :person/name]}] nil)
              new-person-id (-> new-person :people-edit first :db/id)
              new-person-ident [:person/by-id (pu/ensure-tempid new-person-id)]]

          ; reconciler re-runs queries and will re-render the dependant view components immediately
          (is (= (-> new-person :people-edit first :person/name) "")
              "local read returns new person i.e. optimistic update")

          (is (-> @local-state :ui :dirty count (= 1)) "the new person is dirty")

          (mutate-local! {:type :app/edit-person
                          :id   new-person-ident
                          :name "Clark Kent"} nil)

          ; reconciler refreshes dependant views
          (is (= (read-local [{:people-edit [:person/name]}] nil)
                 {:people-edit [{:person/name "Clark Kent"}]})
              "local write is seen by re-render")

          (is (= [] (mutate-local! {:type :app/edit-person
                                    :id   new-person-ident
                                    :name "Clark Kent"} :remote))
              "person edits are not sent to remote")

          ; reconciler runs :remote to see if a remote send is required
          (let [remote-mutation (mutate-local! {:type :app/edit-complete
                                                :id   new-person-ident} :remote)]
            (is (= remote-mutation `[(app/sync
                                       {:db/id       ~new-person-id
                                        :person/name "Clark Kent"})])
                "the full local copy of the new person will be sent to the remote")

            ; reconciler runs local mutation thunk i.e. optimistic update
            (mutate-local! {:type :app/edit-complete :id new-person-ident} nil)
            (is (-> @local-state :ui :dirty count zero?)
                "the new person is not dirty after sync")

            ; reconciler gather-sends fn invokes the remote service, migrates the response
            (let [db-id (-> remote-mutation
                            mutate-remote!
                            (migrate-insert! local-state new-person-id))]

              ; reconciler re-reads after the remote response
              (is (= (read-local root-query)
                     {:people-edit    [{:db/id       db-id
                                        :person/name "Clark Kent"}]
                      :people-display [{:person/name "Clark Kent"}]
                      :cars           []})
                  "queries return the updated id after migrate")))

          ; user adds a new car in the UI
          (mutate-local! {:type :app/add-car :name ""} nil)
          (let [car-id (->> [{:cars [:db/id :car/name]}] read-local :cars first :db/id)
                car-ident [:car/by-id (pu/ensure-tempid car-id)]]
            (mutate-local! {:type :app/edit-car
                            :id   car-ident
                            :name "Corvette Stingray"} nil)
            (is (= [] (mutate-local! {:type :app/edit-car
                                      :id   car-ident
                                      :name "Corvette Stingray"} :remote))
                "no remote calls while editing a car")

            ; user moves focus off the field
            (let [remote-mutation (mutate-local! {:type :app/edit-complete :id car-ident} :remote)]
              (is (= remote-mutation `[(app/sync
                                         {:db/id    ~car-id
                                          :car/name "Corvette Stingray"})])
                  "reconciler gather-sends :edit-complete to the remote")

              (let [db-id (-> remote-mutation
                              mutate-remote!
                              (migrate-insert! local-state car-id))]

                ; reconciler re-reads after the remote response
                (is (= (read-local [{:cars [:db/id :car/name]}])
                       {:cars [{:db/id    db-id
                                :car/name "Corvette Stingray"}]})
                    "queries return the updated car id after migrate"))))

          (let [person1-db-ident (->> (read-local root-query)
                                      :people-edit first :db/id
                                      (vector :person/by-id))]
            ; user changes the person using the db/id
            (mutate-local! {:type :app/edit-person
                            :id   person1-db-ident
                            :name "Superman"} nil)

            ; user completes edit of person and reconciler sends to remote
            (let [remote-update (mutate-local! {:type :app/edit-complete
                                                :id   person1-db-ident} :remote)]
              (mutate-local! {:type :app/edit-complete :id person1-db-ident} nil)
              (mutate-remote! remote-update))

            (is (nil? (mutate-local! {:type :app/edit-complete
                                      :id   person1-db-ident} nil))
                "no mutations generated by controller when record is clean")

            ; checking that remote parser updates and doesn't insert
            (is (= (read-remote [{:people [:person/name]}])
                   {:people [{:person/name "Superman"}]})
                "remote write updates existing person")))))))
