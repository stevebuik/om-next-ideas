(ns om-next-ideas.app.parsing-tests-local
  (:require
    #?(:clj [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])
            [clojure.test :refer [testing are is]]
            [taoensso.timbre :as log]
            [om.next.impl.parser :as p]
            [schema.core :as s]

            [om-next-ideas.core :refer [Id]]
            [om-next-ideas.parsing-utils :as pu]
            [om-next-ideas.app.parsing]))

; tests that exercise the parser without interaction with a server.
; should also be run in cljs / devcards to ensure total portability of code and tests

(s/with-fn-validation
  (let [parse (-> {:read pu/readf :mutate pu/mutate}
                  pu/wrap-throw-exceptions
                  p/parser)
        result-from-server {:people [{:person/id   1000
                                      :person/name "Tony Stark"
                                      :person/cars [{:car/id     2000
                                                     :car/name   "Audi R8"
                                                     :car/engine {:engine/id     3000
                                                                  :engine/torque 540
                                                                  :engine/hp     562}}]}
                                     {:person/id   1001
                                      :person/name "Elon Musk"
                                      :person/cars [{:car/id     2001
                                                     :car/name   "Tesla Roadster"
                                                     :car/engine {:engine/id     3001
                                                                  :engine/torque 270
                                                                  :engine/hp     248}}]}]}
        db (atom (pu/graph->normalized result-from-server #{:person/id :car/id :engine/id}))]

    (testing "read parsing"
      (are [query expected-result]
        (let [parse-result (parse {:state db} query)]
          (= parse-result expected-result))

        ; parameterized join
        `[({:person [:person/name {:person/cars [:car/id :car/name]}]}
            {:person/id [:person/id 1001]})]
        {:person {:person/name "Elon Musk"
                  :person/cars [{:car/id 2001, :car/name "Tesla Roadster"}]}}

        ; all records from an om lookup table
        [{:cars [:car/id :car/name]}]
        {:cars [{:car/id 2000 :car/name "Audi R8"}
                {:car/id 2001 :car/name "Tesla Roadster"}]}

        ; 1 level join
        [{:people [:person/id :person/name]}]
        {:people
         [{:person/id 1000 :person/name "Tony Stark"}
          {:person/id 1001 :person/name "Elon Musk"}]}

        ; 3 level join
        [{:people [:person/id
                   {:person/cars [:car/name
                                  {:car/engine [:engine/torque]}]}]}]
        {:people [{:person/id   1000,
                   :person/cars [{:car/name   "Audi R8",
                                  :car/engine {:engine/torque 540}}]}
                  {:person/id   1001,
                   :person/cars [{:car/name   "Tesla Roadster",
                                  :car/engine {:engine/torque 270}}]}]}))

    (testing "write parsing"

      (parse {:state db} `[(app/add-car {:car/name   "Tesla Model S"
                                         :car/engine {:engine/torque 440
                                                      :engine/hp     362}})])

      (let [new-car-id (->> [{:cars [:car/id :car/name]}]
                            (parse {:state db})
                            :cars (filter #(= "Tesla Model S" (:car/name %))) first :car/id)
            fixed-car-id (pu/ensure-tempid new-car-id)]

        ; leaving this assertion in place so it will fail when the bug is fixed and ensure-tempid is no longer required
        (is (not (instance? om.tempid.TempId new-car-id)) "bug that converts tempids in read parses still exists")

        ; try this with new-car-id to see the tempid bug
        (parse {:state db} `[(app/save-person {:person/id   1001
                                               :person/cars [2001 ~fixed-car-id]})])

        ; optimistic update will re-read the cars list so ensure that the new car is seen by the query
        (is (= (->> `[({:person [:person/name {:person/cars [:car/id :car/name]}]}
                        {:person/id [:person/id 1001]})]
                    (parse {:state db})
                    :person :person/cars
                    (map :car/name))
               ["Tesla Roadster" "Tesla Model S"])
            "current and new (with tempid) car are returned by the read")

        (is (thrown? RuntimeException (parse {:state db} [:error]))
            "read exceptions are thrown")
        ; change level to :error to see middleware logging
        (is (log/with-log-level :fatal (parse {:state db} `[(app/error)]))
            "write exceptions are swallowed (but the middleware can log them)")))))
