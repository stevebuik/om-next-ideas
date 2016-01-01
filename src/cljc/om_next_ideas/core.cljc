(ns om-next-ideas.core
  (:require

    ; portable
    [om.tempid :as tid]

    #?(:clj
    [clojure.pprint :refer [pprint]]
       :cljs [cljs.pprint :refer [pprint]])

    [schema.core :as s])
  #?(:clj
     (:import [om.tempid TempId]
              [java.util UUID])))

; schema. duplicated in remote-core for server

(defn is-uuid? [d] (instance? UUID d))

; TODO can this be done using s/pred without portable instead?
(s/defschema Id #?(:clj  (s/conditional
                           map? {:id (s/pred is-uuid? "is a temp id")}
                           :else s/Num)
                   :cljs (s/conditional
                           #(instance? tid/TempId %) s/Any
                           :else s/Num)))

(s/defschema OmIdent [(s/one s/Keyword "ident key")
                      (s/one Id "ident id")])

; reconciler fns

(s/defn merge-result-tree
  "deep merge maps from b into map a, or replace any other data type"
  [current-state normalized-response]
  (letfn [(merge-tree [a b]
            (if (and (map? a) (map? b))
              (merge-with #(merge-tree %1 %2) a b)
              b))]
    (merge-tree current-state normalized-response)))
