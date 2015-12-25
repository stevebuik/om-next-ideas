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

; schema

(defn is-uuid? [d] (instance? UUID d))

(s/defschema Id (s/conditional map? {:id (s/pred is-uuid? "is a temp id")}
                               :else s/Num))

(s/defschema OmIdent [(s/one s/Keyword "ident key")
                      (s/one Id "ident id")])


