(ns om-next-ideas.core
  (:require

    ; portable
    [om.tempid :as tid]

    #?(:clj
    [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])

    [schema.core :as s]
    [clojure.walk :as wlk])
  #?(:clj
     (:import [om.tempid TempId]
              [java.util UUID])))

; schema

(defn is-tempid? [d] (instance? TempId d))

(s/defschema OmIdent [(s/one s/Keyword "ident key")
                      (s/one (s/either s/Num
                                       (s/pred is-tempid? "is a temp id"))
                             "ident id")])

(s/defn env-with-query
  [query-schema]
  {:query    query-schema
   s/Keyword s/Any})

; parsing fns

(defn dispatch [_ key _] key)

(defmulti readf dispatch)

(defmulti mutate dispatch)

(s/defn parse-join-multiple
  "for a seq of idents, invoke a parse using a supplied read key.
  the invoked read fn will have the ident assoc'd into it's env"
  [{:keys [parser] :as env}
   child-query
   read-key :- s/Keyword                                    ; the key of the read fn
   idents :- [OmIdent]]
  (let [env-key-for-child-read (keyword (str (name read-key) "-id"))
        child-query [{read-key child-query}]]
    (mapv #(-> env
               (assoc env-key-for-child-read %)
               (parser child-query)
               read-key)
          idents)))

(s/defn merge-join-multiple
  "wrapper fn around the parse fn to abbreviate joins"
  [result :- {s/Any s/Any}
   env
   query
   read-key :- s/Keyword
   child-attr :- s/Keyword
   parent :- {s/Any s/Any}]
  (assoc result child-attr
                (parse-join-multiple env query read-key (child-attr parent))))

(s/defn parse-join-single
  "for an idents, invoke a parse using a supplied read key.
  the invoked read fn will have the ident assoc'd into it's env"
  [{:keys [parser] :as env}
   child-query
   child-key :- s/Keyword                                   ; the key of the read fn
   ident :- OmIdent]
  (let [env-key-for-child-read (keyword (str (name child-key) "-id"))
        child-query [{child-key child-query}]]
    (-> env
        (assoc env-key-for-child-read ident)
        (parser child-query)
        child-key)))

(s/defn merge-join-single
  "wrapper fn around the parse fn to abbreviate joins"
  [result :- {s/Any s/Any}
   env
   query
   read-key :- s/Keyword
   child-attr :- s/Keyword
   parent :- {s/Any s/Any}]
  (assoc result child-attr
                (parse-join-single env query read-key (child-attr parent))))

(s/defn get-sub-query
  "find a sub-query/join map in a query"
  [{:keys [query]}
   query-key :- s/Keyword]
  (->> query
       (filter map?)
       (filter #(some #{query-key} (keys %)))
       first
       query-key))

(s/defn get-linked
  "read a record from one of the lookup tables in the state atom.
   the id for the record can be in the env (when used in a recursive join) or
   in the params (when used as a parameterised query)"
  [{:keys [state] :as env}
   params
   table-key :- s/Keyword
   read-key :- s/Keyword]
  (let [env-key (keyword (str (name read-key) "-id"))       ; read from env
        params-key (keyword (name read-key) "id")           ; or read from params
        ident (s/validate OmIdent (or (get env env-key)     ; env has preference
                                      (get params params-key)))
        {:keys [om.next/tables]} @state]
    (get (table-key tables) (last ident))))

(s/defn graph->normalized
  [denormalized
   id-keys :- #{s/Keyword}]
  "transform a query result from a remote service into the om.next normalized shape"
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
(s/defn normalized->graph
  [normalized
   tables]
  "transform a normalized fragment (a client parser query result) into the original data that came from the service"
  (let [id-keys (set (keys tables))]
    (cond
      (is-link? normalized id-keys) (let [table-key (first normalized)
                                          linked (get-in tables [table-key (last normalized)])]
                                      (normalized->graph linked tables))
      (vector? normalized) (mapv #(normalized->graph % tables) normalized)
      (map? normalized) (into {} (map (fn [[k v]]
                                        [k (normalized->graph v tables)]) normalized))
      :default normalized)))

