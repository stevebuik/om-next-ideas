(ns om-next-ideas.parsing-utils
  (:require

    ; portable
    #?(:clj
        [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])

        [schema.core :as s]
        [clojure.walk :as wlk]
        [om.next.impl.parser :as p]

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

; TODO find a way to validate defmulti
(s/defschema is-fn s/Any)
(s/defschema ParserConfig {:read is-fn :mutate is-fn})

(s/defn wrap-throw-exceptions :- ParserConfig
  "middleware that logs exceptions in mutate fns to stop client code from needing to check for them"
  [{:keys [read mutate]} :- ParserConfig]
  {:read   (fn [e k p] (read e k p))
   :mutate (fn [e k p] (let [{:keys [action] :as m} (mutate e k p)]
                         (assoc m
                           :action #(log/log-and-rethrow-errors (action)))))})

(def wrapped-local-parse (-> {:read readf :mutate mutate}
                             wrap-throw-exceptions
                             p/parser))

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
        params-key :db/id                                   ; or read from params
        ident (s/validate OmIdent (or (get env env-key)     ; env has preference
                                      (get params params-key)))
        {:keys [om.next/tables]} @state]
    (log/trace "get linked" {:ident   ident
                             :key     (last ident)
                             :present (contains? (set (keys (table-key tables))) (last ident))
                             :keys    (keys (table-key tables))})
    (get (table-key tables) (last ident))))

(s/defn tree->links
  "returns a lookup table of id -> merged record i.e. all fields in all copies of
  the tree merged into a single record"
  [id-key :- s/Keyword
   denormalized]
  (let [tables (atom {})]
    (wlk/postwalk
      (fn [n]
        (when-let [record-pk (and (map? n) (some #{id-key} (keys n)))]
          (let [link [(get n record-pk)]]
            (swap! tables update-in link merge n)))
        n)
      denormalized)
    @tables))

(s/defn get-lookup-key
  "for a map, return the index key to be used or nil if not indexed.
   the decision is made by looking for a keyword in the map and returning the indents val"
  [idents :- {s/Keyword s/Keyword}
   m :- (s/pred map? "is a map")]
  (some idents (keys m)))

(s/defn tree->normalized
  "normalize a query result.
   id-key is the pk for any record being normalized
   idents is a map where key is a field always present in a record and value is the link key for that record type"
  [denormed :- (s/pred map? "is a map")
   id-key :- s/Keyword
   idents :- {s/Keyword s/Keyword}]
  (let [links (tree->links id-key denormed)
        replacer (fn [n]
                   (if (and (map? n)
                            (get-lookup-key idents n)
                            (get n id-key)
                            (get links (get n id-key)))
                     [(get-lookup-key idents n) (get n id-key)]
                     n))
        tables (->> links
                    ; return nil for path when no lookup key found
                    (map (fn [[k v]]
                           [(when-let [lk (get-lookup-key idents v)]
                              [lk k])
                            v]))
                    ; log and filter if path from prev step is nil
                    (filter (fn [[path value]]
                              (if path
                                [path value]
                                (do
                                  (log/info "cannot denorm" value)
                                  false))))
                    ; load into a map using the path
                    (reduce (fn [a [path value]]
                              (let [value-normed (->> value ; walk map entries but not map
                                                      (map (partial wlk/postwalk replacer))
                                                      (into {}))]
                                (assoc-in a path value-normed)))
                            {}))]
    (assoc
      (wlk/postwalk replacer denormed)
      :om.next/tables tables)))

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

