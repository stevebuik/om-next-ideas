(ns om-next-ideas.figwheel-component
  (:require
    [com.stuartsierra.component :as component]
    [figwheel-sidecar.repl-api :as ra]))


(def figwheel-config
  {:figwheel-options {:css-dirs ["resources/public/css"]}
   :build-ids        ["dev"]
   :all-builds       [{:id           "dev"
                       :figwheel     true
                       :source-paths ["src/cljs" "src/cljc"]
                       :compiler     {:main       'om-next-ideas.app.core
                                      :asset-path "js"
                                      :output-to  "resources/public/js/main.js"
                                      :output-dir "resources/public/js"
                                      :verbose    true}}
                      {:id           "devcards"
                       :figwheel     {:devcards true}
                       :source-paths ["src/cljs" "src/cljc"]
                       :compiler     {:main       'om-next-ideas.app.core
                                      :asset-path "js"
                                      :output-to  "resources/public/js/devcards.js"
                                      :output-dir "resources/public/js"
                                      :verbose    true}}]})

(def handler nil)

(defrecord Figwheel []
  component/Lifecycle
  (start [{:keys [figwheel-config routes] :as c}]
    (def handler (:handler routes))
    (ra/start-figwheel! (assoc-in figwheel-config
                                  [:figwheel-options :ring-handler]
                                  'om-next-ideas.figwheel-component/handler))
    (ra/start-autobuild)
    c)
  (stop [c]
    (ra/stop-autobuild)
    c))

(defn figwheel []
  (component/using (map->Figwheel {:figwheel-config figwheel-config}) [:routes]))

(defn repl []
  (ra/cljs-repl))
