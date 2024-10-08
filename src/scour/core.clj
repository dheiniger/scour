(ns scour.core
  (:require [clojure.java.io :refer [as-url]]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import (org.jsoup Jsoup)
           (org.jsoup.nodes Document Element)))

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

(defn- get-hrefs [^Document doc]
  (when doc
    (let [^Element hrefs (.select doc "[href]")]
      (log/info "Found hrefs from page: " hrefs)
      (map (fn [^Element href]
             (.attr href "abs:href")) hrefs))))

(defn- get-domain [url]
  (:host (bean (as-url url))))

(defn- next-hrefs
  ([domain ^Document doc saved-pages]
   (next-hrefs domain doc saved-pages {}))
  ([domain page saved-pages {:keys [inc-filters] :or {inc-filters []}}]
   (let [standard-filters [(partial of-domain? domain)]
         hrefs (->> (get-hrefs page)
                    (filter (apply some-fn (concat standard-filters inc-filters)))
                    set)]
     (set/difference hrefs (set saved-pages)))))

(defn- finished? [pages limit saved-pages]
  (let [limit-reached? (and limit (>= (count saved-pages) limit))]
    (cond limit-reached? (do (log/info "Limit of" limit "reached.") true)
          (not (seq pages)) (do (log/info "All pages processed.") true)
          :else false)))

(defn throttle [throttle-amt]
  (let [amt (if (sequential? throttle-amt)
              (let [[min max] throttle-amt] (+ (rand-int max) min))
              (or throttle-amt 0))]
    (when (pos? amt)
      (log/info "Waiting " amt "ms..")
      (Thread/sleep amt))))

(defn scan
  "Returns a collection of pages on the same domain as url.
  Pages are represented by tuples in the form of [url page-contents].
  Optionally takes an options map. Options include:
  :inc-filters

  A collection of predicates that are used to include pages that would not otherwise be included.

  :timeout

  The time to wait in ms before skipping a page during the scan

  :limit

  The number of pages to scan before returning.  This is only an approximate limit
  because of the way pages are scanned in batches based on the number of
  hrefs found on a prior page

  :throttle-amt

  The amount of time to wait between each page request.  Can be a number in ms
  or a range of numbers in ms to add randomness such as [100 50000]"
  ([url] (scan url {}))
  ([url {:keys [timeout limit throttle-amt] :or {timeout 20000} :as opts}]
   (let [first-page [(normalize-url url) (download-page url)]]
     (loop [[page & rst :as pages] (set [first-page])
            saved-pages #{first-page}]
       (if-not (finished? pages limit saved-pages)
         (let [[_ content] page
               saved-urls (map first saved-pages)
               next-hrefs (next-hrefs (get-domain url) @content saved-urls opts)
               next-pages (map (fn [href] [href (download-page href timeout)]) next-hrefs)]
           (throttle throttle-amt)
           (recur (concat rst next-pages) (concat saved-pages next-pages)))
         saved-pages)))))

(comment
  (time (def results (scan "https://google.com" {:limit 100 :throttle-amt [100 50000]}))) ;"Elapsed time: 3240.9796 msecs"
  ;;"Elapsed time: 2001.3641 msecs"
  ;;"Elapsed time: 12601.2499 msecs"
  ;;"Elapsed time: 3240.9796 msecs"


  (count results)

  (defn example-filter [s]
    (str/ends-with? s "/example"))

  (def results (scan "https://example.com/" {:inc-filters [example-filter] :timeout 1000 :throttle 1000}))
  (map first results)
  )


