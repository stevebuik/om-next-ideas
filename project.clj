(defproject om-next-ideas "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.374" ]

                 ;; Logging
                 [org.clojure/tools.logging "0.3.1"]
                 [com.taoensso/timbre "4.1.4"]

                 [com.stuartsierra/component "0.3.0"]
                 [prismatic/schema "0.4.3"]
                 [com.rpl/specter "0.6.2"]

                 [org.omcljs/om "1.0.0-alpha28"]]

  :profiles {:dev {:source-paths  ["dev"
                                   "src/clj"
                                   "src/cljc"]
                   :clean-targets ^{:protect false} ["resources/public/js" :target]
                   :dependencies  [[org.clojure/test.check "0.8.2"]]}}

  )
