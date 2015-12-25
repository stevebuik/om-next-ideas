(ns om-next-ideas.parsing-utils
  (:require

    ; portable
    #?(:clj
        [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])

        [schema.core :as s]
        [clojure.walk :as wlk]

        [om-next-ideas.core :refer [OmIdent]]
        [om.tempid :as tid]
        [taoensso.timbre :as log])
  #?(:clj
     (:import (java.util UUID))))

(s/defn env-with-query
  [query-schema]
  {:query    query-schema
   s/Keyword s/Any})

; parsing fns

(defn dispatch [_ key _] key)

(defmulti readf dispatch)

(defmulti mutate dispatch)

(s/defn temp-id
  [k :- s/Keyword]
  [k #?(:cljs (tid/tempid)
        :clj  (tid/tempid (UUID/randomUUID)))])

(defn ensure-tempid
  "workaround the bug that transforms tempids into maps in query results"
  [n]
  (if (and (map? n)
           (contains? (set (keys n)) :id)
           (instance? UUID (:id n)))
    (tid/tempid (:id n))
    n))

(s/defschema is-fn s/Any)                                   ; TODO find a way to validate defmulti
(s/defschema ParserConfig {:read is-fn :mutate is-fn})

(s/defn wrap-throw-exceptions :- ParserConfig
  "middleware that logs exceptions in mutate fns to stop client code from needing to check for them"
  [{:keys [read mutate]} :- ParserConfig]
  {:read   (fn [e k p] (read e k p))
   :mutate (fn [e k p] (let [{:keys [action] :as m} (mutate e k p)]
                         (assoc m
                           :action #(log/log-and-rethrow-errors (action)))))})

(s/defn parse-join-multiple
  "for a seq of idents, invoke a parse using a supplied read key.
  the invoked read fn will have the ident assoc'd into it's env"
  [{:keys [parser] :as env}
   child-query
   read-key :- s/Keyword                                    ; the key of the read fn
   idents :- [OmIdent]]
  (let [env-key-for-child-read (keyword (str (name read-key) "-id"))
        child-query [{read-key child-query}]
        results (mapv #(-> env
                           (assoc env-key-for-child-read %)
                           (parser child-query)
                           read-key)
                      idents)]
    (log/trace "parse join multiple" {:cq child-query
                                      :k  env-key-for-child-read})
    results))

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
   in the params (when used by a parameterised query)"
  [{:keys [state] :as env}
   params
   table-key :- s/Keyword
   read-key :- s/Keyword]
  (let [env-key (keyword (str (name read-key) "-id"))       ; read from env
        params-key (keyword (name read-key) "id")           ; or read from params
        ident (s/validate OmIdent (or (get env env-key)     ; env has preference
                                      (get params params-key)))
        {:keys [om.next/tables]} @state]
    (log/trace "get linked" {:ident   ident
                             :key     (last ident)
                             :present (contains? (set (keys (table-key tables))) (last ident))
                             :keys    (keys (table-key tables))})
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

