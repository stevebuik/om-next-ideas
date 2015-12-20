(ns om-next-ideas.core
  (:require
    #?(:clj [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])
            [schema.core :as s]
            [clojure.walk :as wlk]))


(s/defn db->normalized
  [denormalized
   id-keys :- #{s/Keyword}]
  (let [tables (atom {})]
    (merge
      (wlk/postwalk
        (fn [n]
          (if-let [record-pk (and (map? n) (some id-keys (keys n)))]
            (let [link [record-pk (get n record-pk)]]
              (swap! tables update-in link merge n)
              link)
            n))
        denormalized)
      {:om.next/tables @tables})))

(defn is-link?
  [n id-keys]
  (and (vector? n) (= 2 (count n)) (id-keys (first n))))

; cannot use postwalk here because it sees map entries as a [k v] and that is then mistaken as a link
(s/defn normalized->db
  [normalized
   tables]
  (let [id-keys (set (keys tables))]
    (cond
      (is-link? normalized id-keys) (let [table-key (first normalized)
                                          linked (get-in tables [table-key (last normalized)])]
                                      (normalized->db linked tables))
      (vector? normalized) (mapv #(normalized->db % tables) normalized)
      (map? normalized) (into {} (map (fn [[k v]]
                                        [k (normalized->db v tables)]) normalized))
      :default normalized)))

