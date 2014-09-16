(ns hn-app.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.core.logic :as l]
            [clojure.tools.macro :as m]
            [clojure.walk :as w]
            [clostache.parser :as cs])
  (:use [clojure.pprint]
        [hn-app.mail]))

;; *** Start Config ***

(defn env [var-name]
  "If env var exists, return it, else barf."
  (let [v (System/getenv var-name)]
    (if v v (throw (ex-info (str "env var " var-name " missing") {})))))

(def c {:file "/tmp/.hn"
        :words-to-match ["functional" "clojure" "clj" "lisp" "linux"]
        :base-url "https://news.ycombinator.com/"
        :min-comment-count 100
        :page-size 30
        :email-user (env "HN_EMAIL_USERNAME")
        :email-pass (env "HN_EMAIL_PASSWORD")})

;; *** End Config ***

;; *** Util Start ***

(defn find-first-num [string]
  (let [first-num (re-find #"\d+" string)]
    (if (not (nil? first-num)) (Integer/parseInt first-num) 0)))

(defn str-contains-any? [words str]
  (some (set (map str/upper-case words))
        (map str/upper-case (re-seq #"\w+" str)))) 

;; *** Util End ***

;; *** Start HTTP/HTML ***

(defn http-req
  "Make an HTTP request, return an input stream."
  [url]
  (-> (java.net.URL. url)
      .openConnection
      ;; Set the User-Agent header or 403 is the result.
      (doto (.setRequestProperty "User-Agent" "Mozilla/5.0"))
      .getContent))

(defn fetch-page
  "Fetch hn page n, digest into nodes w/ enlive."
  [base-url n]
  (html/html-resource (http-req (str base-url "news?p=" n))))

(defn find-a-tags [root]
  (let [stories (atom [])]
    (w/prewalk (fn [n] (when (= :a (:tag n)) (swap! stories conj n)) n) root)
    @stories))

;; *** End HTTP/HTML ***

;; *** Start HTML -> core.logic -> news items ***

(defn starts-with
  "Relation where x is a string which starts with s"
  [x s]
  (l/project [x] (l/== true (.startsWith x s))))

(defn slice-of-four
  "Relation where l is a collection, and s represents a 'slice' of 4
  consecutive elements from the collection."
  [l s]
  (m/symbol-macrolet
    [_ (l/lvar)]
    (l/fresh [a b c d r]
             (l/== (l/llist a r) l)
             (l/== (l/llist b c d _) r)
             (l/conde
               [(l/== [a b c d] s)]
               [(slice-of-four r s)]))))

(defn find-news-items*
  "From a sequence of a-tag pairs (link, content), find news items."
  [seq-of-a-pairs]
  (l/run* [c d g h]
        (l/fresh [lof a b e f]
                 (slice-of-four seq-of-a-pairs lof)
                 (l/== lof [[a b] [c d] [e f] [g h]])
                 (starts-with a "vote")
                 (starts-with e "user")
                 (starts-with g "item"))))

(defn find-news-items
  "From a hn page (seq of nodes via Enlive) for each news item, return
  map of the item name, link, comment count, and comment link."
  [page]
  (->> page
       first
       find-a-tags
       (map (juxt (comp :href :attrs) (comp first :content)))
       find-news-items*
       (map (fn [item] {:item-name (nth item 1)
                        :item-link (nth item 0)
                        :comment-count (find-first-num (nth item 3))
                        :comment-link (str (c :base-url) (nth item 2))}))))

;; *** End HTML -> core.logic -> news items ***

;; *** Start news item fns ****

(defn item->id [item] (find-first-num (:comment-link item)))

;; *** End news item fns ****

;; *** Start app-state handling ***

(defn new-app-state [] {:previously-matched-ids #{}})

(defn set-app-state! [app-state] (spit (c :file) app-state))

(defn get-app-state
  "Get info stored in file."
  []
  (try (read-string (slurp (c :file)))
       (catch java.io.FileNotFoundException e
         (set-app-state! (new-app-state)))))

;; *** End app-state handling ***

;; *** Start news item matching/filtering ***

(defn headline-match?
  [item words-to-match]
  (str-contains-any? words-to-match (:item-name item)))

(defn comment-match?
  [item min-comment-count]
  (>= (:comment-count item) min-comment-count))

(defn get-matching-news-items
  "Find news items that match our criteria."
  [items words-to-match min-comment-count]
  (filter (fn [i] (or (headline-match? i words-to-match)
                      (comment-match? i min-comment-count))) items))

(defn prev-matched?
  [matched-item-ids item]
  ((set matched-item-ids) (item->id item)))

;; *** End news item matching/filtering ***

;; *** Start output formatting ***

(defn ->a [link content] (cs/render "<a href=\"{{l}}\">{{c}}</a>"
                                    {:l link :c content}))

(defn item->a [i]
  (str (->a (:item-link i) (:item-name i)) " - "
       (->a (:comment-link i) (str (:comment-count i) " comments"))))

;; TODO - both ->h, ->p use size, content. Normalize?

(defn ->h [size content] (cs/render "<h{{s}}>{{c}}}</h{{s}}>"
                                    {:s size :c content}))

(defn ->p [font-size content]
  (cs/render "<p style=\"font-size:{{s}}px;\">{{{c}}}</p>"
             {:s font-size :c content}))

(defn ->html*
  "Create HTML from news items + a heading, with sizes."
  [items heading-text font-size h-size]
  (str (->h h-size heading-text)
       (->p font-size (apply str (interpose "<br/>" (map item->a items))))))

(defn ->html
  "Create HTML from news items."
  [new prev discarded]
  (let [f-size 14
        h-size 1
        ->smaller-font (fn [size] (- size 3))
        ->smaller-h (fn [size] (+ size 1))]
    (str (->html* new "News" f-size h-size)
         (->html* discarded "Discarded" (->smaller-font f-size) 
                  (->smaller-h h-size))
         (->html* prev "Previous" (->smaller-font (->smaller-font f-size))
                  (->smaller-h (->smaller-h h-size))))))

;; *** End output formatting ***

;; *** Start email ***

(defn send-mail!
  [msg]
  (send-gmail {:from "redsourceinfo@gmail.com"
               :to ["jebbeich@gmail.com"]
               :subject "filtered hn"
               :text msg
               :user (c :email-user)
               :password (c :email-pass)}))

;; *** End email ***

;; *** Start main job ***

(defn get-matches
  "From the page, find all:
  * news items that match our criteria
  * matching news items that have already been reported
  * news items that were unmatched (discarded)"
  [page words-to-match min-comment-count prev-matched-ids]
  (let [all-news-items (find-news-items page)
        matched (get-matching-news-items all-news-items words-to-match
                                         min-comment-count)
        discarded (set/difference (set all-news-items) (set matched))
        prev-matched (filter (partial prev-matched? prev-matched-ids) matched)
        new-matched (set/difference (set matched) (set prev-matched))]
    {:new new-matched :previous prev-matched :discarded discarded}))

(defn -main [& argv]
  "Find new matches, email/output them, update app-state."
  (let [email? (= "email" (nth argv 0))
        app-state (get-app-state)
        page (fetch-page (c :base-url) 1)
        m (get-matches page (c :words-to-match)
                           (c :min-comment-count)
                           (:previously-matched-ids app-state))
        html (apply ->html (map m [:new :previous :discarded]))]
    (if email?
      (do
        (when (:new m) (send-mail! html))
        (set-app-state! (update-in app-state [:previously-matched-ids]
                                   set/union (set (map item->id (:new m))))))
      (println html))))

