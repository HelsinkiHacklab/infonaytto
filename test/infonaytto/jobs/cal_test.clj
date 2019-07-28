(ns infonaytto.jobs.cal-test
  (:require [clojure.test :refer [deftest is]]
            [com.stuartsierra.component :as component]
            [infonaytto.onyx.submit :as submit]
            [infonaytto.onyx.dev-system :refer [onyx-dev-env]]
            [onyx.api]))

(deftest test-cal-job
  ;; peer number = no. distinct tasks in the workflow
  (let [dev-env (component/start (onyx-dev-env 8))]
    (try 
      (let [{:keys [out]} (submit/dev-submit-cal-job dev-env)
            first-out (first out)]
        (is (:success first-out))
        (is (:db-success first-out))
        (is (< 0 (count first-out)))
        )
      (finally 
        (component/stop dev-env)))))
