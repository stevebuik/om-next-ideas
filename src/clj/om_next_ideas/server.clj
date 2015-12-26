(ns om-next-ideas.server
  (:require
    [clojure.pprint :refer [pprint]]
    [taoensso.timbre :as log]
    [ring.util.response :refer [response file-response resource-response]]
    [ring.adapter.jetty :refer [run-jetty]]
    [bidi.bidi :as bidi]
    [cognitect.transit :as transit]
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
  (let [reader (transit/reader in :json)]
    (transit/read reader)))

(s/defn clj->transit :- s/Str
  [d]
  (let [out (ByteArrayOutputStream. 4096)
        writer (transit/writer out :json)]
    (transit/write writer d)
    (.toString out)))

(defn generate-response [data & [status]]
  {:status  (or status 200)
   :headers {"Content-Type" "application/transit+json"}
   :body    data})

(defn handler
  ""
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
                  (api/handle-api-request parser)
                  clj->transit
                  generate-response)))))

;; =============================================================================
;; WebServer

(defrecord WebServer [port handler]
  component/Lifecycle
  (start [{:keys [parser-api] :as component}]
    (let [req-handler (partial handler parser-api)
          container (run-jetty req-handler
                               {:port port :join? false})]
      (assoc component :container container)))
  (stop [{:keys [container] :as component}]
    (.stop container)
    (dissoc component :container)))

(defn new-server []
  (component/using (->WebServer 8080 handler) [:parser-api]))

