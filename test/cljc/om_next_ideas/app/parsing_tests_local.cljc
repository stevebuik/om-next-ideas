(ns om-next-ideas.app.parsing-tests-local
  (:require
    #?(:clj [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])
            [clojure.test :refer [are is]]
            [taoensso.timbre :as log]
            [om.next.impl.parser :as p]
            [schema.core :as s]

            [om-next-ideas.core :refer [readf graph->normalized]]
            [om-next-ideas.app.parsing]))

; tests that exercise the parser without interaction with a server

(s/with-fn-validation
  (let [parse (p/parser {:read readf})
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
        db (atom (graph->normalized result-from-server #{:person/id :car/id :engine/id}))]

    (are [query result]
      (log/log-and-rethrow-errors
        (= (parse {:state db} query) result))

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
                                :car/engine {:engine/torque 270}}]}]}
      )

    #_(pprint (parse {:state db} [{:people [:person/id
                                            {:person/cars [:car/name
                                                           {:car/engine [:engine/torque]}]}]}]))
    ))
