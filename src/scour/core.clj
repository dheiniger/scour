(ns scour.core
  (:require [clojure.java.io :refer [as-url]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import (org.jsoup Jsoup)))

(defn- download-page
  ([url] (download-page url 2000))
  ([url timeout]
   (future
     (try
       (log/info "Getting page for url " url)
       (.get (.timeout (Jsoup/connect url) timeout))
       (catch Exception e
         (log/warn "Failed to open url " url " due to " (.getMessage e)))))))

(defn- remove-last-slash [url]
  (if (str/ends-with? url "/")
    (apply str (drop-last url))
    url))

(defn- normalize-url [url]
  (remove-last-slash (str/trim url)))

(defn- of-domain? [domain url]
  (let [domain (str/replace domain "https://" "")
        pattern (re-pattern domain)]
    (some? (re-find pattern url))))

(defn- get-hrefs [^org.jsoup.nodes.Document doc]
  (when doc
    (let [^org.jsoup.nodes.Element hrefs (.select doc "[href]")]
      (log/info "Found hrefs from page: " hrefs)
      (map (fn [^org.jsoup.nodes.Element href]
             (.attr href "abs:href")) hrefs))))

(defn- get-domain [url]
  (:host (bean (as-url url))))

(defn- next-hrefs
  ([domain ^org.jsoup.nodes.Document doc saved-pages]
   (next-hrefs domain doc saved-pages {}))
  ([domain page saved-pages {:keys [inc-filters] :or {inc-filters []}}]
   (let [standard-filters [(partial of-domain? domain)]
         hrefs (->> (get-hrefs page)
                    (filter (apply some-fn (concat standard-filters inc-filters)))
                    set)]
     (set/difference hrefs (set saved-pages)))))

(defn scan
  ([url] (scan url {}))
  ([url opts]
   (let [first-page [(normalize-url url) (download-page url)]]
     (loop [[page & rst :as pages] (set [first-page])
            saved-pages #{first-page}]
       (if (seq pages)
         (let [[_ content] page
               saved-urls (map first saved-pages)
               next-hrefs (next-hrefs (get-domain url) @content saved-urls opts)
               next-pages (map (fn [href] [href (download-page href)]) next-hrefs)]
           (recur (concat rst next-pages) (concat saved-pages next-pages)))
         saved-pages)))))

(comment


  (def results (scan "https://example.com/"))
  (count results)

  (defn example-filter [s]
    (str/ends-with? s "/example"))

  (def results (scan "https://example.com/" {:inc-filters [example-filter]}))
  (map first results)
  ;(def results (scan "https://soundcloud.com/" {:inc-filters [mp3-filter]}))
  )


