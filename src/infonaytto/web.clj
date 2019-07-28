(ns infonaytto.web
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [com.walmartlabs.lacinia.util :refer [attach-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia :refer [execute]]
            [infonaytto.db :as db]
            [infonaytto.views.layout :as layout]
            [infonaytto.views.content :as content]))

(def env (-> "env.edn" io/resource slurp read-string))

; ----------------------------
; Graphql resolver functions:

(defn cal-find-events [context arguments value]
  (let [{:keys               [start end keyword]} arguments
        idlist               (db/zrangebyscore* "cal:items" start end)
        eventlist            (when (not-empty idlist) (db/mget* idlist))]
    (flatten eventlist)))


(defn cal-get-events-id [context arguments value]
  (let [{:keys [start end]}  arguments
        dbresult             (db/zrangebyscore* "cal:items" start end)]
    (map #(hash-map :id (str %)) dbresult))) ; voisi vähän muotoilla

(defn cal-get-events [context arguments value]
  (let [{:keys [start end]}  arguments
        dbresult-list        (db/zrangebyscore* "cal:items" start end)
        dbresult-events      (db/mget* dbresult-list)]
    (flatten dbresult-events)))


(defn cal-get-event-by-id [context arguments value]
  (let [{:keys [id]}  arguments
        dbresult              (db/get* id)]
    dbresult))

(defn cal-get-events-by-ids [context arguments value]
  (let [{:keys [id]}  arguments
        dbresult      (db/mget* (:eventlist arguments) )]
    (first dbresult)))

(defn cal-get-event-strings [context arguments value]
  (let [{:keys [start end]}  arguments
        dbresult             (db/zrangebyscore* "cal:items" start end)]
    dbresult))

(defn kanban-get-stacks [context arguments value]
  (let [dbresult             (db/smembers* "kanban:stacks")]
    dbresult))

(defn kanban-get-stack-items [context arguments value]
  (let [{:keys [stackref]} arguments]
    (flatten (for [ref stackref]
      (let [dbdata           (db/hget* ref "data")
            dbcardref        (db/hget* ref "cards")
            dbcards          (db/smembers* dbcardref)]
        (hash-map :data  (hash-map :title "asdf")
                  :cards dbcards))))))

(defn kanban-find-cards [context arguments value]
  ; TODO
  )
(defn kanban-resolve-stacks [context arguments value]
  (let [kanban-stacks (db/smembers* "kanban:stacks")]
    (flatten (for [k-ref kanban-stacks]
      (let [dbdata    (db/hget* k-ref "data")
            dbcardref (db/hget* k-ref "cards")
            dbcards   (db/smembers* dbcardref)]
        (assoc dbdata
          :cards
          (for [c-ref dbcards]
            (let [dbcard (db/get* c-ref)]
              dbcard
              ))) )) )))

(defn kanban-resolve-cards [context arguments value]
  ; TODO
  )


; ---------------
; Graphql schemas

(defn make-schema [file resolvermap]
  (-> file
    (io/resource)
    (io/reader)
    (java.io.PushbackReader.)
    (edn/read)
    (attach-resolvers resolvermap)
    (schema/compile)))

(def cal-schema
  (make-schema
    "schemas/cal.edn"
    {:cal-resolve-events cal-get-events
     :cal-resolve-event-details cal-get-events-by-ids
     }))

(def kanban-schema
  (make-schema
    "schemas/kanban.edn"
    {:kanban-resolve-stacks kanban-resolve-stacks
     :kanban-resolve-cards  kanban-resolve-cards}))

; ----------------
; Request handlers


(defn cal-handler [query]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (let [result (execute cal-schema query nil nil)]
           (json/write-str result))})

(defn kanban-handler [query]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (let [result (execute kanban-schema query nil nil)]
           (json/write-str result))})

; ---------------
; Routing

(defroutes app
  ; The app
  (GET "/" [] (layout/application (-> env :html :title) "auto" (content/auto)))
  
  ; Graphql
  (GET "/graphql/cal/:query" [query] (cal-handler query))
  (GET "/graphql/kanban/:query" [query] (kanban-handler query))
  (POST "/graphql/cal" request (cal-handler (slurp (:body request))))
  (POST "/graphql/kanban" request (kanban-handler (slurp (:body request))))
  
  ; Resources
  (route/resources "/")
  
  ; Default -> front page
  (route/not-found (layout/application (-> env :html :title) "auto" (content/auto)))
)
