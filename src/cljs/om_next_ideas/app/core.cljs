(ns om-next-ideas.app.core
  (:require
    [cljs.pprint :refer [pprint]]
    [goog.dom :as gdom]
    [cognitect.transit :as t]
    [om.transit :as ot]
    [om.next :as om :refer-macros [defui]]

    [om-next-ideas.core :refer [merge-result-tree]]
    [om-next-ideas.app.mutation-controller :as controller]
    [om-next-ideas.parsing-utils :as pu :refer [readf mutate]]
    [om-next-ideas.app.parsing]
    [om-next-ideas.app.views :as v]
    [schema.core :as s])
  (:import [goog.net XhrIo]))

(enable-console-print!)

; client state is figwheel-reloadable
(defonce db (atom {}))

(defn transit-post [url]
  (fn [{:keys [remote]} cb]
    (.send XhrIo url
           (fn [_]
             (this-as this
               (cb (t/read (ot/reader) (.getResponseText this)))))
           "POST" (t/write (ot/writer) remote)
           #js {"Content-Type" "application/transit+json"})))

(def reconciler
  (let [parse (fn [source msg-type msg]
                (->> (assoc msg :type msg-type)
                     controller/message->mutation
                     ; TODO how to use the logging wrapper here?
                     (om/transact! source)))]
    (om/reconciler
      {:state      db
       :normalize  false                                    ; using cljc normalize fns
       :shared     {:send! parse}
       :parser     (om/parser {:read readf :mutate mutate})
       :send       (transit-post "/api")
       :merge-tree pu/portable-merge})))

(om/add-root! reconciler v/App (gdom/getElement "app"))

