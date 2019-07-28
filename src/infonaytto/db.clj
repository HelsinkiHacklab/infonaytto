(ns infonaytto.db
  (:require [taoensso.carmine :as car :refer (wcar)]
            [clojure.java.io :as io]
            [clojure.edn :as edn] ))

(def env (-> (io/resource "env.edn") (io/reader) (java.io.PushbackReader.) (edn/read)))
(declare dbconnection)
(defmacro wcar* [& body] `(car/wcar dbconnection ~@body))

(case (-> env :db :type)
  :redis
    (def dbconnection {:pool {} :spec (:spec (:db env))})
    
)

(defn keys* [k]
  (wcar* (car/keys k)))

(defn set* [k v]
  (wcar* (car/set k v)))

(defn get* [k]
  (wcar* (car/get k)))

(defn mget* [klist]
  (wcar* (apply car/mget klist)))

(defn zadd* [k i v]
  (wcar* (car/zadd k i v)))

(defn zrange* [k l u]
  (wcar* (car/zrange k l u )))

(defn zrangebyscore* [k l u]
  (wcar* (car/zrangebyscore k l u )))

(defn sadd* [k v]
  (wcar* (car/sadd k v)))

(defn smembers* [k]
  (wcar* (car/smembers k)))

(defn smembers-get* [k]
  (wcar* (map #(car/get %) (car/smembers k))))

(defn hset* [k f v]
  (wcar* (car/hset k f v)))

(defn hget* [k f]
  (wcar* (car/hget k f)))

(defn del* [k]
  (wcar* (car/del k)))
