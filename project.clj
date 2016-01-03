(defproject om-next-ideas "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.2.374" :exclusions [org.clojure/tools.reader]]

                 ;; Logging
                 [org.clojure/tools.logging "0.3.1"]
                 [com.taoensso/timbre "4.1.4"]

                 [com.stuartsierra/component "0.3.0"]
                 [prismatic/schema "1.0.4"]
                 [com.rpl/specter "0.6.2"]

                 [bidi "1.20.3" :exclusions [ring/ring-core]]
                 [ring/ring "1.4.0" :exclusions [commons-codec]]
                 [com.cognitect/transit-clj "0.8.285" :exclusions [commons-codec]]
                 [com.cognitect/transit-cljs "0.8.232"]

                 [com.datomic/datomic-free "0.9.5344" :exclusions [javax.mail/mail org.apache.httpcomponents/httpclient commons-logging]]

                 [org.clojure/clojurescript "1.7.170"]
                 [org.omcljs/om "1.0.0-alpha28" :exclusions [commons-codec]]
                 [cljsjs/react "0.14.3-0"]
                 [sablono "0.3.6" :exclusions [cljsjs/react]]]

  :profiles {:dev {:source-paths  ["dev"
                                   "src/clj"
                                   "src/cljs"
                                   "src/cljc"
                                   "test/cljc"
                                   "test/clj"]
                   :plugins       [[quickie "0.4.1"]]
                   :clean-targets ^{:protect false} ["resources/public/js" :target]
                   :dependencies  [[figwheel-sidecar "0.5.0-1" :scope "provided"
                                    :exclusions [commons-codec ring/ring-core org.codehaus.plexus/plexus-utils org.clojure/tools.reader org.clojure/clojurescript]]
                                   [devcards "0.2.0-8" :scope "provided" :exclusions [cljsjs/react]]

                                   [org.clojure/test.check "0.9.0"]]}})
