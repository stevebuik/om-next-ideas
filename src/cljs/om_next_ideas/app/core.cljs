(ns om-next-ideas.app.core
  (:require
    [cljs.pprint :refer [pprint]]
    [goog.dom :as gdom]
    [cognitect.transit :as t]
    [om.next :as om :refer-macros [defui]]

    [om-next-ideas.app.mutation-controller :as controller]
    [om-next-ideas.parsing-utils :as pu :refer [readf mutate]]
    [om-next-ideas.app.parsing]
    [om-next-ideas.app.views :as v])
  (:import [goog.net XhrIo]))

(enable-console-print!)

(defonce db (atom {}))

(defn transit-post [url]
  (fn [{:keys [remote]} cb]
    (.send XhrIo url
           (fn [e]
             (this-as this
               (cb (t/read (t/reader :json) (.getResponseText this)))))
           "POST" (t/write (t/writer :json) remote)
           #js {"Content-Type" "application/transit+json"})))

(def reconciler
  (om/reconciler
    {:state     db
     :normalize true
     :shared    {:send! (fn [source msg]
                          (->> msg
                               controller/message->mutation
                               (om/transact! source)))}
     :parser    (om/parser {:read readf :mutate mutate})
     :send      (transit-post "/api")}))

(om/add-root! reconciler v/App (gdom/getElement "app"))
