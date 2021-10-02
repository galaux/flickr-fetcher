(ns flickr-fetcher.core
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [flickr-fetcher.image :as image]
            [muuntaja.core :as m]
            [reitit.coercion.spec :as spec]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [ring.adapter.jetty :as jetty]
            [ring.util.http-response :refer [no-content]]))

(s/def ::count pos-int?)
(s/def ::width pos-int?)
(s/def ::height pos-int?)
(s/def ::body
  (s/keys :opt-un [::count
                   ::width
                   ::height]))

(def ^:private default-params
  {:count  20
   :height 500
   :width  500})

(def ^:private base-url
  "https://api.flickr.com/services/feeds/photos_public.gne?jsoncallback=%s&format=json")

(defn image-handler
  [config req]
  (let [{:keys [count width height]} (merge default-params
                                            (get-in req [:parameters :body]))]
    (image/fetch! config count width height))
  (no-content))

(defn app
  [config]
  (ring/ring-handler
    (ring/router
      ["/api/flickr"
       {:post {:middleware [muuntaja/format-middleware
                            rrc/coerce-request-middleware]
               :coercion   spec/coercion
               :parameters {:body ::body}
               :muuntaja   m/instance
               :handler    (partial image-handler config)}}])))

(Thread/setDefaultUncaughtExceptionHandler
 (reify
   Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (log/error {:what :uncaught-exception
                 :exception ex
                 :where (str "Uncaught exception on" (.getName thread))}))))

(defn start-http
  [config]
  (jetty/run-jetty (app config)
                   {:port  8080
                    :join? false}))

(defn -main
  [& args]
  (let [save-dir       (System/getenv "SAVE_DIR")
        parallel-count (-> (System/getenv "PARALLEL_COUNT")
                           Integer/parseInt)
        sleep-grace    (-> (System/getenv "SLEEP_GRACE")
                           Integer/parseInt)]
    (when-not (some-> save-dir io/file .isDirectory)
      (throw (Exception. "Cannot find directory described by `SAVE_DIR`")))
    (let [predictable-callback-name "callback"
          config {:save-dir       save-dir
                  :sleep-grace    sleep-grace
                  :parallel-count parallel-count
                  :url            (format base-url predictable-callback-name)
                  :callback-name  predictable-callback-name}]
      (start-http config))))
