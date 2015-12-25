(ns om-next-ideas.core-test
  (:require
    #?(:clj [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])
            [schema.core :as s]

    #?(:clj
            [schema.experimental.generators :as s-gen])

            [clojure.test.check.generators]
            [clojure.test.check.clojure-test]

            [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]

            [om-next-ideas.parsing-utils :as pu]
            [om-next-ideas.core :refer :all]))

; schemas used to generate sample databases for round-trip tests
(s/defschema Car {:car/id                    s/Int
                  (s/optional-key :car/name) s/Str})

(s/defschema Person {:person/id                    s/Int
                     (s/optional-key :person/name) s/Str
                     (s/optional-key :person/cars) [Car]})

(s/defn de-dupe
  "HOF returning a fn that removes duplicates from a seq in a map using a primary key"
  [seq-key :- s/Keyword
   pk :- s/Keyword]
  (s/fn [item-map]
    (->> item-map
         seq-key
         (group-by pk)
         (mapv (fn [[_ v]] (first v))))))

#?(:clj
   (deftest normalization-round-tripping-generative-tests

     (let [generator (gen/fmap
                       (de-dupe :people :person/id)
                       (s-gen/generator {:people [Person]}))
           prop #(prop/for-all [query-result generator]
                               (let [ident-keys #{:person/id :car/id}]
                                 (let [{:keys [om.next/tables people]} (pu/graph->normalized query-result ident-keys)]
                                   (= (pu/normalized->graph people tables) (:people query-result)))))]
       ;(pprint (gen/sample generator 3))
       (is (:result (tc/quick-check 50 (prop)))))))

; add any examples found by test.check in here for regression coverage
(deftest normalization-round-tripping-example-tests
  (s/with-fn-validation

    (are [query-result ident-keys]
      (let [{:keys [om.next/tables people]} (pu/graph->normalized query-result ident-keys)
            round-tripped (pu/normalized->graph people tables)]

        (when (not= round-tripped (:people query-result))
          (pprint {:before (:people query-result)
                   :after  round-tripped}))
        (is (= round-tripped (:people query-result))))

      (s/validate {:people [Person]} {:people [{:person/id   1000
                                                :person/name "Tony Stark"
                                                :person/cars [{:car/id   2000
                                                               :car/name "Audi R8"}]}
                                               {:person/id   1001
                                                :person/name "Elon Musk"
                                                :person/cars [{:car/id   2001
                                                               :car/name "Tesla Roadster"}]}]})
      #{:person/id :car/id})))

(run-tests)