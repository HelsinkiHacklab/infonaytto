(ns infonaytto.jobs.kanban-test
  (:require [clojure.test :refer [deftest is]]
            [com.stuartsierra.component :as component]
            [infonaytto.onyx.submit :as submit]
            [infonaytto.onyx.dev-system :refer [onyx-dev-env]]
            [onyx.api]))

(deftest test-kanban-job
  ;; peer number = no. distinct tasks in the workflow
  (let [dev-env (component/start (onyx-dev-env 10))]
    (println dev-env)
    (try 
      (let [{:keys [out]}  (submit/dev-submit-kanban-job dev-env)
            first-out (first out)]
        (is (:db-success first-out))
        (is (:success first-out))
        )
      (finally 
        (component/stop dev-env)))))

