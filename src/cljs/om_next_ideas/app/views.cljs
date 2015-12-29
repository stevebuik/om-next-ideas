(ns om-next-ideas.app.views
  (:require
    [cljs.pprint :refer [pprint]]
    ; om.dom must be required or get "Uncaught ReferenceError: ReactDOM is not defined"
    ; this is because sablano still uses react 0.13. when it moves to 0.14, remove om.dom
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]))

(defui Person
  static om/IQuery
  (query [_]
    [:db/id :person/name])
  Object
  (render [this]
    (let [{:keys [send!]} (om/shared this)
          {:keys [person/name]} (om/props this)]
      (html
        [:div name]))))

(defui App
  static om/IQuery
  (query [_]
    [{:people (om/get-query Person)}])
  Object
  (render [this]
    (let [{:keys [send!]} (om/shared this)
          {:keys [people]} (om/props this)]
      (html
        [:div
         [:a {:href "#"
              :on-click #(send! this {:type :app/new-person})} "New Person"]
         [:div (str people)]]))))
