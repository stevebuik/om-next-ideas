(ns om-next-ideas.app.views
  (:require
    [cljs.pprint :refer [pprint]]
    ; om.dom must be required or get "Uncaught ReferenceError: ReactDOM is not defined"
    ; this is because sablano still uses react 0.13. when it moves to 0.14, remove om.dom
    [om.dom :as dom]
    [om.next :as om :refer-macros [defui]]
    [taoensso.timbre :as log]
    [sablono.core :refer-macros [html]]))

(defui CarEditor
  static om/Ident
  (ident [_ {:keys [db/id]}]
    [:car/by-id id])
  static om/IQuery
  (query [_]
    [:db/id :car/name])
  Object
  (render [this]
    (let [{:keys [send!]} (om/shared this)
          {:keys [car/name]} (om/props this)]
      (log/debug :render-car-editor (om/props this))
      (html
        [:div
         [:input {:value      name
                  :auto-focus true
                  :type       "text"
                  :on-change  #(send! this :app/edit-car {:id   (om/get-ident this)
                                                             :name (.. % -target -value)})
                  :on-blur    #(send! this :app/edit-complete {:id (om/get-ident this)})}]]))))

(defui PersonEditor
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
      (log/debug :render-person-editor (om/props this))
      (html
        [:div
         [:input {:value      name
                  :auto-focus true
                  :type       "text"
                  :on-change  #(send! this :app/edit-person {:id   (om/get-ident this)
                                                             :name (.. % -target -value)})
                  :on-blur    #(send! this :app/edit-complete {:id (om/get-ident this)})}]]))))

(defui PersonDisplay
  static om/Ident
  (ident [_ {:keys [db/id]}]
    [:person/by-id id])
  static om/IQuery
  (query [_]
    [:person/name])
  Object
  (render [this]
    (let [{:keys [person/name]} (om/props this)]
      (log/debug :render-person-display (om/props this))
      (html
        [:div {:style #js {:paddingBottom "1px"}} name]))))

(def car-edit (om/factory CarEditor))
(def person-edit (om/factory PersonEditor))
(def person-display (om/factory PersonDisplay))

(defui App
  static om/IQuery
  (query [_]
    [{:people-edit (om/get-query PersonEditor)}
     {:people-display (om/get-query PersonDisplay)}
     {:cars (om/get-query CarEditor)}])
  Object
  (render [this]
    (let [{:keys [send!]} (om/shared this)
          {:keys [people-edit people-display cars]} (om/props this)
          half-style #js {:padding "10px"
                          :width   "40%"
                          :float   "left"}]
      (log/debug :render-app (keys (om/props this)))
      (html
        [:div
         [:div
          [:p "Instructions"]
          [:ol
           [:li "Click 'New Person' to add a new record"]
           [:li "Then edit the record and see the re-renders on the right side"]
           [:li "Move the focus off the input to see the remote sync of the local record"]
           [:li "Re-focus on the input but make no changes. Move focus and notice no remote sync i.e. not dirty"]]]
         [:div
          [:h2 "People"]
          [:a {:href     "#"
               :on-click #(send! this :app/add-person {:name ""})}
           "New Person"]]
         [:div {:style half-style}
          [:form {:onSubmit #(.preventDefault %)}
           (map person-edit people-edit)]]
         [:div {:style half-style}
          (map person-display people-display)]
         [:div {:style #js {:clear "both"}}]
         [:div
          [:h2 "Cars"]
          [:a {:href     "#"
               :on-click #(send! this :app/add-car {:name ""})}
           "New Car"]]
         [:div
          [:form {:onSubmit #(.preventDefault %)}
           (map car-edit cars)]]]))))
