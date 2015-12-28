(ns om-next-ideas.test-utils
  (:require [clojure.string :as string]))

(def log-config
  {:output-fn (fn [{:keys [level ?ns-str msg_] :as d}]
                (str (string/upper-case (name level)) " " ?ns-str " >> " (force msg_)))})
