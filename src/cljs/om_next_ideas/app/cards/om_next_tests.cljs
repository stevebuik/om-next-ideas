(ns om-next-ideas.app.cards.om-next-tests
  (:require-macros
    [devcards.core :refer [defcard deftest]])
  (:require
    [cljs.test :refer-macros [is async]]
    [om.next :as om :refer-macros [defui]]
    [om.dom :as dom]))

(def p
  (om/parser
    {:read   (fn [_ _ _] {:quote true})
     :mutate (fn [_ _ _] {:quote true})}))

(def r
  (om/reconciler
    {:parser  p
     :ui->ref (fn [c] (-> c om/props :id))}))

(defui Binder
  Object
  (componentDidMount [this]
    (let [indexes @(get-in (-> this om/props :reconciler) [:config :indexer])]
      (om/update-state! this assoc :indexes indexes)))
  (render [this]
    (binding [om/*reconciler* (-> this om/props :reconciler)]
      (apply dom/div nil
             (when-let [indexes (get-in (om/get-state this)
                                        [:indexes :ref->components])]
               [(dom/p nil (pr-str indexes))])))))

(def binder (om/factory Binder))

(defcard basic-nested-component
         "Test that component nesting works"
         (binder {:reconciler r}))

(deftest test-indexer
         "Test indexer"
         (let [idxr (get-in r [:config :indexer])]
           (is (not (nil? idxr)) "Indexer is not nil in the reconciler")
           (is (not (nil? @idxr)) "Indexer is IDeref")))

