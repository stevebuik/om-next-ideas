(ns om-next-ideas.core-test
  (:require
    #?(:clj [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])
            [schema.core :as s]
            [clojure.test :refer :all]
            [om-next-ideas.core :refer :all]))

(deftest normalization
  (s/with-fn-validation
    (let [query-result {:people [{:person/id   1000
                                  :person/name "Tony Stark"
                                  :person/cars [{:car/id   2000
                                                 :car/name "Audi R8"}]}
                                 {:person/id   1001
                                  :person/name "Elon Musk"
                                  :person/cars [{:car/id   2001
                                                 :car/name "Tesla Roadster"}]}]}]
      (let [ident-keys #{:person/id :car/id}
            {:keys [om.next/tables people]} (db->normalized query-result ident-keys)]
        (is (= (normalized->db people tables) (:people query-result)))))))

(run-tests)