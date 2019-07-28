(ns infonaytto.onyx.submit
  (:require [com.stuartsierra.component :as component]
            [clojure.java.io :refer [resource]]
            [infonaytto.cal :as cal]
            [infonaytto.kanban :as kanban]
            [onyx.api]
            [clojure.edn :as edn]
            )
  (:import (java.time ZonedDateTime))
)


(defn submit-cal-job [env] 
  (let [cfg             (-> "peer-config.edn" resource slurp read-string)
        peer-config     (assoc cfg :onyx/tenancy-id (:onyx-id env))
        catalog         (cal/cal-build-catalog 10 20)
        lifecycles      (cal/cal-build-lifecycles)
        workflow        cal/cal-workflow
        flow-conditions cal/cal-flow-conditions
        input-data [{:start-date (java.time.ZonedDateTime/now)
                     :end-date   (.plusDays (java.time.ZonedDateTime/now) 365)
                     :env        (-> "env.edn" resource slurp read-string)}]]
    ;; Automatically pipes the data structure into the channel
    
    (cal/bind-inputs! lifecycles {:in input-data})
    
    (let [job {:workflow workflow
               :catalog catalog
               :lifecycles lifecycles
               :flow-conditions flow-conditions
               :task-scheduler :onyx.task-scheduler/balanced}
          ret (onyx.api/submit-job peer-config job)]
      (when-not (:success? ret)
        (throw (ex-info "Job submission was not successful." ret)))

      ;; Automatically grab output from the stubbed core.async channels,
      ;; returning a vector of the results with data structures representing
      ;; the output.
      (cal/collect-outputs! lifecycles [:out]) )))




(defn submit-kanban-job [env] 
  (let [cfg             (-> "peer-config.edn" resource slurp read-string)
        peer-config     (assoc cfg :onyx/tenancy-id (:onyx-id env))
        catalog         (kanban/kanban-build-catalog 10 20)
        lifecycles      (kanban/kanban-build-lifecycles)
        workflow        kanban/kanban-workflow
        flow-conditions kanban/kanban-flow-conditions
        input-data [{:env  (-> "env.edn" resource slurp read-string)}]]
    ;; Automatically pipes the data structure into the channel
    
    (kanban/bind-inputs! lifecycles {:in input-data}) ;;;; ei toimi ilman!
    
    (let [job {:workflow workflow
               :catalog catalog
               :lifecycles lifecycles
               :flow-conditions flow-conditions
               :task-scheduler :onyx.task-scheduler/balanced}
          ret (onyx.api/submit-job peer-config job)]
      (when-not (:success? ret)
        (throw (ex-info "Job submission was not successful." ret)))

      ;; Automatically grab output from the stubbed core.async channels,
      ;; returning a vector of the results with data structures representing
      ;; the output.
      (kanban/collect-outputs! lifecycles [:out]) )))
