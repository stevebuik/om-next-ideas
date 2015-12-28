(ns om-next-ideas.remote-core
  (:require [schema.core :as s])
  (:import (java.util UUID)))

; schema. duplicated in core ns for client

(defn is-uuid? [d] (instance? UUID d))

(s/defschema Id (s/conditional map? {:id (s/pred is-uuid? "is a temp id")}
                               :else s/Num))

(s/defschema OmIdent [(s/one s/Keyword "ident key")
                      (s/one Id "ident id")])


; server parsing base fns : important that these are different to the client fns
; so that they can be used together when doing integration tests

(defn dispatch [_ key _] key)

(defmulti readf dispatch)

(defmulti mutate dispatch)

