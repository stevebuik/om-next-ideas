(ns om-next-ideas.server
  (:require
    [clojure.pprint :refer [pprint]]
    [taoensso.timbre :as log]
    [ring.middleware.resource :refer [wrap-resource]]
    [ring.util.response :refer [resource-response]]
    [ring.adapter.jetty :refer [run-jetty]]
    [bidi.bidi :as bidi]
    [cognitect.transit :as t]
    [om.transit :as ot]
    [com.stuartsierra.component :as component]
    [om-next-ideas.remote-parser :as api]
    [schema.core :as s])
  (:import (java.io InputStream ByteArrayOutputStream)))

(def routes
  ["" {"/"            :index
       "/favicon.ico" :fav-icon
       "/api"
                      {:post {[""] :api}}}])

(s/defn transit-inputstream->clj
  [in :- (s/pred #(instance? InputStream %))]
  (let [reader (ot/reader in)]
    (t/read reader)))

(s/defn clj->transit :- s/Str
  [d]
  (let [out (ByteArrayOutputStream. 4096)
        writer (ot/writer out)]
    (t/write writer d)
    (.toString out)))

(defn handler
  [parser req]
  (let [match (bidi/match-route routes (:uri req)
                                :request-method (:request-method req))]
    (log/log-and-rethrow-errors
      (case (:handler match)
        :fav-icon {:body "n/a"}
        :index (assoc (resource-response "html/index.html" {:root "public"})
                 :headers {"Content-Type" "text/html"})
        :api (->> (:body req)
                  transit-inputstream->clj
                  (api/parse parser)
                  clj->transit
                  (assoc {:status  200
                          :headers {"Content-Type" "application/transit+json"}}
                    :body))))))

(defrecord Routes []
  component/Lifecycle
  (start [{:keys [parser-api] :as component}]
    (let [req-handler (-> (partial handler parser-api)
                          (wrap-resource "public"))]
      (assoc component :handler req-handler)))
  (stop [component]
    (dissoc component :handler)))

(defn new-routes []
  (component/using (->Routes) [:parser-api]))

;; =============================================================================
;; WebServer

(defrecord WebServer [port handler]
  component/Lifecycle
  (start [{:keys [routes] :as component}]
    (let [container (run-jetty (:handler routes)
                               {:port port :join? false})]
      (assoc component :container container)))
  (stop [{:keys [container] :as component}]
    (.stop container)
    (dissoc component :container)))

(defn new-server []
  (component/using (->WebServer 8080 handler) [:routes]))

