(ns infonaytto.onyx.system
  (:require [clojure.core.async :refer [chan <!!]]
            [clojure.java.io :refer [resource]]
            [com.stuartsierra.component :as component]
            [onyx.plugin.core-async]
            [onyx.api]))

(def env-configs (-> "env-config.edn" resource slurp read-string))
(def peer-configs (-> "peer-config.edn" resource slurp read-string))


(defrecord OnyxEnv [n-peers]
  component/Lifecycle

  (start [component]
    (println "Starting Onyx environment")
    (let [onyx-id (java.util.UUID/randomUUID)
          env-config (assoc env-configs :onyx/tenancy-id onyx-id)
          peer-config (assoc peer-configs :onyx/tenancy-id onyx-id)
          env (onyx.api/start-env env-config)
          peer-group (onyx.api/start-peer-group peer-config)
          peers (onyx.api/start-peers n-peers peer-group)]
      (assoc component
         :env env
         :onyx-id onyx-id
         :peer-group peer-group
         :peers peers)))

  (stop [component]
    (println "Stopping Onyx environment")

    (doseq [v-peer (:peers component)]
      (onyx.api/shutdown-peer v-peer))

    (onyx.api/shutdown-peer-group (:peer-group component))
    (onyx.api/shutdown-env (:env component))

    (assoc component
      :env nil
      :peer-group nil
      :peers nil)))


(defn onyx-env [n-peers refname]
  (println refname)
  (map->OnyxEnv {:n-peers n-peers}))

