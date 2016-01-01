(ns om-next-ideas.app.views
  (:require
    [cljs.pprint :refer [pprint]]
    ; om.dom must be required or get "Uncaught ReferenceError: ReactDOM is not defined"
    ; this is because sablano still uses react 0.13. when it moves to 0.14, remove om.dom
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [sablono.core :refer-macros [html]]))

(defui Person
  static om/Ident
  (ident [_ {:keys [db/id]}]
    [:person/by-id id])
  static om/IQuery
  (query [_]
    [:db/id :person/name])
  Object
  (render [this]
    (let [{:keys [send!]} (om/shared this)
          {:keys [person/name]} (om/props this)]
      (pprint [:render-person (om/props this)])
      (html
        [:div
         [:input {:value      name
                  :auto-focus true
                  :type       "text"
                  :on-change  #(send! this :app/edit-person {:id   (om/get-ident this)
                                                             :name (.. % -target -value)})
                  :on-blur    #(send! this :app/edit-complete {:id (om/get-ident this)})}]]))))

(def person (om/factory Person))

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
         [:a {:href     "#"
              :on-click #(send! this :app/add-person {:name ""})}
          "New Person"]
         [:div
          [:form {:onSubmit #(.preventDefault %)}
           (map person people)]]]))))
