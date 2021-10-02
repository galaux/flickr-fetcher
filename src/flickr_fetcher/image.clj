(ns flickr-fetcher.image
  (:require [clj-http.client :as http]
            [clj-http.conn-mgr :as conn-mgr]
            [clj-http.core :as http-core]
            [clojure.core.async :as async]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [image-resizer.resize :refer [resize-fn]]
            [clojure.tools.logging :as log])
  (:import [java.io ByteArrayInputStream File]
           javax.imageio.ImageIO))

(defn extract-json-str
  "Cumbersome but efficient way to circumvent the fact the API returns JSONP:
   Response looks like `callback(ACTUAL_JSON) so removing prefix and trailing
   parenthesis`"
  [{:keys [callback-name]} body]
  (let [prefix-len              (inc (count callback-name))
        length-minus-last-paren (dec (count body))]
    (subs body prefix-len length-minus-last-paren)))

;; TODO handle retry
(defn get-one-page!
  [{:keys [url] :as config} base-req]
  (let [req (merge base-req
                   {:method :get
                    :url    url})
        resp (->> (http/request req)
                  :body
                  (extract-json-str config))]
    (->> (json/read-str resp :key-fn keyword)
         :items
         (map #(get-in % [:media :m])))))

(defn get-count-urls!
  [{:keys [sleep-grace] :as config} base-req img-count]
  (loop [acc-urls []]
    (let [urls     (get-one-page! config base-req)
          acc-urls (distinct (concat acc-urls urls))]
      (if (>= (count acc-urls) img-count)
        (take img-count acc-urls)
        (do
          ;; Let's not harass Flickr so that we don't get black listed
          (log/info "waiting for more input from Flickr")
          (Thread/sleep sleep-grace)
          (recur acc-urls))))))

(defn url-str->filename
  [url-str]
  (-> url-str
      io/as-url
      .getPath
      io/file
      .getName))

;; TODO handle retry
;; TODO handle errors
(defn get-image
  [base-req url]
  (let [req (merge base-req
                   {:method :get
                    :url    url
                    :as     :byte-array})
        {:keys [status body]} (http/request req)
        file-name (url-str->filename url)]
    {:img       body
     :file-name file-name}))

(defn byte-array->buffered-image
  [byte-arr]
  (ImageIO/read (ByteArrayInputStream. byte-arr)))

;; TODO support other format than jpg
(defn save-buffered-image!
  [dir {:keys [file-name img]}]
  (let [absolute-file-name (str dir "/" file-name)]
    (ImageIO/write img "jpg" (File. absolute-file-name))))

(defn batch-run
  "Apply transducer `xf` to `data` in a `parallel-count` sized sliding window."
  [parallel-count xf data]
  (let [out (async/chan)]
    (async/pipeline-blocking parallel-count out xf (async/to-chan data))
    (async/<!! (async/into [] out))))

(defn make-base-req
  [parallel-count]
  (let [cm (conn-mgr/make-reusable-conn-manager
            {:threads           parallel-count
             :default-per-route parallel-count})]
    {:connection-manager cm
     :http-client        (http-core/build-http-client {} false cm)}))

(defn fetch!
  [{:keys [save-dir parallel-count] :as config}
   img-count width height]
  (let [
        base-req (make-base-req parallel-count)
        ;; Create a transducer for memory/time efficiency to prevent
        ;; intermediate steps results from being kept in memory
        xf (comp
            (map (partial get-image base-req))
            (map #(update % :img byte-array->buffered-image))
            (map #(update % :img (resize-fn width height)))
            ;; TODO More image transformation steps … ?
            (map (partial save-buffered-image! save-dir)))
        urls (get-count-urls! config base-req img-count)]
    (batch-run parallel-count xf urls)))
