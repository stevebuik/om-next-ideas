(ns om-next-ideas.core-test
  (:require
    #?(:clj [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])

            [schema.core :as s]
            [clojure.walk :as wlk]

    #?(:clj
            [schema.experimental.generators :as s-gen])

    #?(:clj
            [clojure.test.check.generators])
    #?(:clj
            [clojure.test.check.clojure-test])

    #?(:clj
            [clojure.test :refer [deftest run-tests testing are is]]
       :cljs [cljs.test :refer-macros [are testing is]])

    #?(:clj
            [clojure.test.check :as tc])
    #?(:clj
            [clojure.test.check.generators :as gen])
    #?(:clj
            [clojure.test.check.properties :as prop])

            [om-next-ideas.parsing-utils :as pu]
            [taoensso.timbre :as log]))

; schemas used to generate sample databases for round-trip tests

(s/defschema PersonId (s/pred pos? "person id"))
(s/defschema CarId (s/pred pos? "car id"))
(s/defschema EngineId (s/pred pos? "engine id"))

#?(:clj
   (defn pos-int-generator
     "returns a integer generator for integers above a min value"
     [min]
     (gen/fmap #(+ % min) gen/nat)))

(s/defschema Engine {:db/id                      EngineId
                     :engine/torque              s/Int
                     (s/optional-key :engine/hp) s/Int})

(s/defschema Car {:db/id                       CarId
                  :car/name                    s/Str
                  (s/optional-key :car/engine) Engine})

(s/defschema Person {:db/id                        PersonId
                     :person/name                  s/Str
                     (s/optional-key :person/cars) [Car]})

(s/defn de-dupe
  "HOF returning a fn that removes duplicate records from a generated map
  id-key is the pk for any record
  seq-keys are the keys at any level that should be distinct after de-duping"
  [id-key :- s/Keyword
   seq-keys :- #{s/Keyword}]
  (fn [with-dupes]
    (let [links (atom {})]
      (->> with-dupes
           ; replace all records with the first match seen
           (wlk/postwalk
             (fn [n]
               (if-let [id (and (map? n) (id-key n))]
                 (if-let [seen (get @links id)]
                   seen
                   (do (swap! links assoc id n)
                       n))
                 n)))
           ; de-dupe any nested collections
           (wlk/postwalk
             (fn [n]
               (if (and (vector? n) (seq-keys (first n)))
                 [(first n) (vec (distinct (second n)))]
                 n)))))))

#?(:clj
   (deftest normalization-round-tripping-generative-tests

     (let [generator (gen/fmap
                       (de-dupe :db/id #{:people :person/cars})
                       (s-gen/generator {:people [Person]} {PersonId (pos-int-generator 1000)
                                                            CarId    (pos-int-generator 2000)
                                                            EngineId (pos-int-generator 3000)}))
           prop #(prop/for-all [query-result generator]
                               (s/with-fn-validation
                                 (let [ident-keys {:person/name   :person/by-id
                                                   :car/name      :car/by-id
                                                   :engine/torque :engine/by-id}]
                                   ;(pprint query-result)
                                   (let [{:keys [om.next/tables people]} (pu/tree->normalized query-result :db/id ident-keys)]
                                     (= (pu/normalized->tree people tables) (:people query-result))))))]
       ;(pprint (gen/sample generator 5))
       (let [check (tc/quick-check 50 (prop))
             success (= true (:result check))]
         (when-not success (pprint check))
         ; :result can contain an exception so check against true
         (is success "check had no errors")))))

; add any examples found by test.check in here for regression coverage
(deftest normalization-round-tripping-example-tests
  (log/log-and-rethrow-errors
    (s/with-fn-validation

      (are [query-result ident-keys]
        (let [{:keys [om.next/tables people]} (pu/tree->normalized query-result :db/id ident-keys)
              round-tripped (pu/normalized->tree people tables)]

          (when (not= round-tripped (:people query-result))
            (pprint {:before (:people query-result)
                     :after  round-tripped}))
          (is (= round-tripped (:people query-result))))

        (s/validate {:people [Person]} {:people [{:db/id       1000
                                                  :person/name "Tony Stark"
                                                  :person/cars [{:db/id      2000
                                                                 :car/name   "Audi R8"
                                                                 :car/engine {:db/id         3000
                                                                              :engine/torque 150}}]}
                                                 {:db/id       1001
                                                  :person/name "Elon Musk"
                                                  :person/cars [{:db/id    2001
                                                                 :car/name "Tesla Roadster"}]}]})
        {:person/name   :person/by-id
         :car/name      :car/by-id
         :engine/torque :engine/by-id}))))

; #?(:clj (run-tests))