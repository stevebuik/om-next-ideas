(ns user
  (:require
    [clojure.tools.namespace.repl :refer (refresh)]
    [figwheel-sidecar.repl-api :as ra]
    [om-next-ideas.system :as system]
    [om-next-ideas.figwheel-component :as fw]))

(def system nil)

(defn init
  "Constructs the current development system."
  []
  (alter-var-root #'system (constantly (system/system "datomic:mem:/ideas" {:figwheel (fw/figwheel)}))))

(comment
  ; use any of the fns in the ra ns to perform figwheel operations e.g.
  (ra/reset-autobuild)

  ; TODO eval in cljs from repl not working until piggieback is added
  ; if you want a cljs repl after the system is started
  (ra/cljs-repl))

(defn start
  "Starts the current development system."
  []
  (alter-var-root #'system system/start)
  :started)

(defn stop
  "Shuts down and destroys the current development system."
  []
  (alter-var-root #'system (fn [s] (when s (system/stop s))))
  :stopped)

(defn go
  "Initializes the current development system and starts it running."
  []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))