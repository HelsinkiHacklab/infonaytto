(ns infonaytto.core
  (:require [infonaytto.web :as web]
            [ring.adapter.jetty :as jetty]
            [tea-time.core :as tt]
            [com.stuartsierra.component :as component]
            [infonaytto.onyx.submit :as submit]
            [infonaytto.onyx.system :refer [onyx-env]]
            [infonaytto.onyx.dev-system :refer [onyx-dev-env]]
            [onyx.api]
           )
  (:gen-class)
  )


(defn update-cal []
  (let [env (component/start (onyx-env 8 "cal"))]
    (submit/submit-cal-job env)
    (component/stop env)))

(defn update-kanban []
  (let [env (component/start (onyx-env 5 "kanban"))]
    (submit/submit-kanban-job env)
    (component/stop env)))


(defn -main
  [& args]
  (tt/start!)
  (tt/every! (* 60 10) 0 (bound-fn [] (update-cal))) ; 10 min
  (tt/every! (* 60 10) 30 (bound-fn [] (update-kanban)))
  (jetty/run-jetty web/app {:port 3000})
)
