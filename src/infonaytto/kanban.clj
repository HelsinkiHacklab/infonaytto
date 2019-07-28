(ns infonaytto.kanban
  (:require [clojure.java.io :as io]
            [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.edn :as edn]
            [infonaytto.db :as db]
            [markdown-to-hiccup.core :as mth]
            
            [clojure.core.async :refer [chan sliding-buffer >!!]]
            [onyx.plugin.core-async :refer [take-segments!]]
            
            ))

(def env (-> "env.edn" (io/resource) (io/reader) (java.io.PushbackReader.) (edn/read)))

(defn request-kanban [params]
  ; Request for new calendar data
  (let [req (try
               @(http/get (:deck-url env)
                          {:basic-auth (:basic-auth env)
                           :headers {"OCS-APIRequest" "true"}})
               (catch Exception ex 
                 false ))]
    (if req
      (assoc params :success true :response (json/read-str (:body req) :key-fn keyword)) ;;; keywordize!!
      (assoc params :success false)) ))




(defn copy-to-db [params]
  "Copy into database, flow the data through, add db success status"
    
  (when (:success params)
    (try
      (let [old-stacks (set (db/keys* "kanban:stack:*"))
            old-cards  (set (db/keys* "kanban:card:*"))]

        (doseq [stack (:response params)]
          (let [stack-date    0;(.toEpochSecond (:lastModified stack))
                stack-data    (select-keys stack [:title :order :lastModified :deletedAt])
                stack-id      (str "kanban:stack:" (hash (select-keys stack [:boardId :id])))
                cards-id      (str "kanban:cards:" (hash (select-keys stack [:boardId :id])))]
            (db/hset* stack-id "data" stack-data)
            (db/hset* stack-id "cards" cards-id)
    
            ; Add found stacks in a set
            (db/sadd* "kanban:stacks" stack-id)
    
            (doseq [card (:cards stack)]
              (let [card-id  (str "kanban:card:" (hash (select-keys card [:boardId :stackId :id])))]
                (db/sadd* "kanban:cards" card-id)
                (db/sadd* cards-id card-id)
                (db/set*  card-id  card)))
        ))
        
        ; Find differencies between new items and old items
        ; Delete everything that is not included in latest batch
        (let [new-stacks (set (db/smembers* "kanban:stacks"))
              new-cards  (set (db/smembers* "kanban:cards"))
              diff-stack (clojure.set/difference old-stacks new-stacks)
              diff-card  (clojure.set/difference old-cards new-cards)]
          (doseq [del-item diff-stack] (db/del* del-item) )
          (doseq [del-item diff-card] (db/del* del-item) ))

        (assoc params :db-success true))
      (catch Exception ex (assoc params :db-success false)))
    ))




(defn kanban-build-catalog [batch-size batch-timeout]
  [{:onyx/name   :in
    :onyx/plugin :onyx.plugin.core-async/input
    :onyx/type   :input
    :onyx/medium :core.async
    :onyx/max-peers 1
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size
    :onyx/doc "Reads segments from a core.async channel"}

   {:onyx/name :get-kanban
    :onyx/fn   :infonaytto.kanban/request-kanban
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}
   

   {:onyx/name :db-store-kanban
    :onyx/fn   :infonaytto.kanban/copy-to-db
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name   :out
    :onyx/plugin :onyx.plugin.core-async/output
    :onyx/type   :output
    :onyx/medium :core.async
    :onyx/max-peers 1
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size
    :onyx/doc "Writes segments to a core.async channel"}

 ])


(def kanban-workflow
  [[:in                 :get-kanban]
   [:get-kanban         :db-store-kanban]
   [:db-store-kanban    :out]
  ])



(def input-channel-capacity 10000)
(def output-channel-capacity (inc input-channel-capacity))

(def get-input-channel
  (memoize
   (fn [id]
     (chan input-channel-capacity))))

(def get-output-channel
  (memoize
   (fn [id]
     (chan (sliding-buffer output-channel-capacity)))))

(defn channel-id-for [lifecycles task-name]
  (:core.async/id
   (->> lifecycles
        (filter #(= task-name (:lifecycle/task %)))
        (first))))

(defn bind-inputs! [lifecycles mapping]
  (doseq [[task segments] mapping]
    (let [in-ch (get-input-channel (channel-id-for lifecycles task))]
      (doseq [segment segments]
        (>!! in-ch segment)))))

(defn collect-outputs! [lifecycles output-tasks]
  (->> output-tasks
       (map #(get-output-channel (channel-id-for lifecycles %)))
       (map #(take-segments! % 5000))
       (zipmap output-tasks)))

(defn inject-in-ch [event lifecycle]
  {:core.async/buffer (atom {})
   :core.async/chan (get-input-channel (:core.async/id lifecycle))})

(defn inject-out-ch [event lifecycle]
  {:core.async/chan (get-output-channel (:core.async/id lifecycle))})

(def in-calls
  {:lifecycle/before-task-start inject-in-ch})

(def out-calls
  {:lifecycle/before-task-start inject-out-ch})


(defn kanban-build-lifecycles []
  [{:lifecycle/task :in
    :core.async/id (java.util.UUID/randomUUID)
    :lifecycle/calls :infonaytto.kanban/in-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.core-async/reader-calls}
   
   {:lifecycle/task :out
    :lifecycle/calls :infonaytto.kanban/out-calls
    :core.async/id (java.util.UUID/randomUUID)
    :lifecycle/doc "Lifecycle for writing to a core.async chan"}
   {:lifecycle/task :out
    :lifecycle/calls :onyx.plugin.core-async/writer-calls}])


(def kanban-flow-conditions
  [])
