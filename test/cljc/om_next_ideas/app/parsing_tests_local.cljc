(ns om-next-ideas.app.parsing-tests-local
  (:require
    #?(:clj [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])
            [clojure.test :refer [run-tests deftest testing are is]]
            [taoensso.timbre :as log]
            [om.next.impl.parser :as p]
            [schema.core :as s]

            [om-next-ideas.core :refer [Id]]
            [om-next-ideas.parsing-utils :as pu]
            [om-next-ideas.app.parsing]))

; tests that exercise the parser without interaction with a server.
; should also be run in cljs / devcards to ensure total portability of code and tests

(deftest local-parse-crud
  (log/log-and-rethrow-errors
    (s/with-fn-validation
      (let [parse pu/wrapped-local-parse                    ; alias for brevity
            result-from-server {:people [{:db/id       1000
                                          :person/name "Tony Stark"
                                          :person/cars [{:db/id      2000
                                                         :car/name   "Audi R8"
                                                         :car/engine {:db/id         3000
                                                                      :engine/torque 540
                                                                      :engine/hp     562}}]}
                                         {:db/id       1001
                                          :person/name "Elon Musk"
                                          :person/cars [{:db/id      2001
                                                         :car/name   "Tesla Roadster"
                                                         :car/engine {:db/id         3001
                                                                      :engine/torque 270
                                                                      :engine/hp     248}}]}]}
            db (atom (pu/tree->normalized result-from-server :db/id {:person/name   :person/by-id
                                                                     :car/name      :car/by-id
                                                                     :engine/torque :engine/by-id}))]

        #_(log/with-log-level
            :debug
            (pprint
              (parse {:state db} [{:people [:db/id :person/name
                                            {:person/cars [:db/id :car/name
                                                           {:car/engine [:db/id :engine/torque :engine/hp]}]}]}])))

        (testing "read parsing"
          (are [query expected-result]
            (let [parse-result (parse {:state db} query)]
              (= parse-result expected-result))

            ; parameterized join
            `[({:person [:person/name {:person/cars [:db/id :car/name]}]}
                {:db/id [:person/by-id 1001]})]
            {:person {:person/name "Elon Musk"
                      :person/cars [{:db/id 2001, :car/name "Tesla Roadster"}]}}

            ; all records from an om lookup table
            [{:cars [:db/id :car/name]}]
            {:cars [{:db/id 2000 :car/name "Audi R8"}
                    {:db/id 2001 :car/name "Tesla Roadster"}]}

            ; 1 level join
            [{:people [:db/id :person/name]}]
            {:people
             [{:db/id 1000 :person/name "Tony Stark"}
              {:db/id 1001 :person/name "Elon Musk"}]}

            ; 3 level join
            [{:people [:db/id
                       {:person/cars [:db/id :car/name
                                      {:car/engine [:engine/torque]}]}]}]
            {:people [{:db/id       1000,
                       :person/cars [{:db/id      2000
                                      :car/name   "Audi R8",
                                      :car/engine {:engine/torque 540}}]}
                      {:db/id       1001,
                       :person/cars [{:db/id      2001
                                      :car/name   "Tesla Roadster",
                                      :car/engine {:engine/torque 270}}]}]})

          (is (thrown? RuntimeException (parse {:state db} [:error]))
              "read exceptions are thrown"))

        (testing "write parsing"

          (parse {:state db} `[(app/add-car {:car/name   "Tesla Model S"
                                             :car/engine {:engine/torque 440
                                                          :engine/hp     362}})])

          (let [new-car-id (->> [{:cars [:db/id :car/name]}]
                                (parse {:state db})
                                :cars (filter #(= "Tesla Model S" (:car/name %))) first :db/id)
                fixed-car-id (pu/ensure-tempid new-car-id)]


            ; leaving this assertion in place so it will fail when the bug is fixed and ensure-tempid is no longer required
            (is (not (instance? om.tempid.TempId new-car-id)) "bug that converts tempids in read parses still exists")

            ; try this with new-car-id to see the tempid bug
            (parse {:state db} `[(app/save-person {:db/id       1001
                                                   :person/cars [2001 ~fixed-car-id]})])

            ; optimistic update will re-read the cars list so ensure that the new car is seen by the query
            (is (= (->> `[({:person [:person/name {:person/cars [:db/id :car/name]}]}
                            {:db/id [:person/by-id 1001]})]
                        (parse {:state db})
                        :person :person/cars
                        (map :car/name))
                   ["Tesla Roadster" "Tesla Model S"])
                "current and new (with tempid) car are returned by the read")

            ; change level to :error to see middleware logging
            (is (log/with-log-level :fatal (parse {:state db} `[(app/error)]))
                "write exceptions are silently swallowed (but the middleware can log them)")))))))

(run-tests)