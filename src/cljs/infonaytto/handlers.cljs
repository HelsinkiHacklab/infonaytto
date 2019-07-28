(ns infonaytto.handlers
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]])
  (:require [reagent.core :as r :refer [atom]]
            [ajax.core :as ajax]
            [ajax.util :as ajaxu]
            [re-frame.core :refer [reg-event-db reg-event-fx]]
            [infonaytto.state :refer [initial-state]]
            [infonaytto.views :as i]
            [infonaytto.kanban :as kanban]
            [day8.re-frame.http-fx]
            ))
(enable-console-print!)


; User interface actions handlers

(reg-event-db
  :initialize
  (fn
    [db _]
    (merge db initial-state)))

(reg-event-db
  :set-current-page
  (fn
    [db [_ page]]
    (assoc db :current-page page)))

(reg-event-db
  :kanban-selection
  (fn
    [db [_ card-stack-map]]
    (assoc-in db [:kanban :user :selection] card-stack-map)))

(reg-event-db
  :auto-toggle
  (fn
    [db _]
    (println "auto " (:auto db))
    (assoc db :auto (not (:auto db)))))




; Custom format for "application/graphql"
(defn graphql-request-format []
  {:write (ajaxu/to-utf8-writer identity)
   :content-type "application/graphql"})


(reg-event-fx
  :update-app-data
  (fn [{:keys [db]} [_ app]]
    {:http-xhrio {:method :post
                  :uri (-> db :app-static app :graphql)
                  :format (graphql-request-format)
                  :params ((-> db :app-static app :query) db)
                  :timeout 10000 ; 1000 ei riitä !!=?!?!???!!!
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success [::good-http-result app]
                  :on-failure [::error-http-result app]}
     :db (assoc-in db [app :status] :loading)}))



(reg-event-db
  ::good-http-result
  (fn [db [_ app result]]
    (let [errors (-> result :errors first :message)]
      (println app)
      (println result)
      (if (some? errors)
        ; There is an error
        (case errors
          "Failed to parse GraphQL query."  (assoc-in db [app :status] :parse-error)
          (assoc-in db [app :status] :error)) ; else general error
        
        ; No errors, continue
        (case app
          :cal (-> db
              (assoc-in [app :status] :ready)
              (assoc-in [app :user :start] (+ (-> db (get app) :user :start)(-> db (get app) :user :interval)))
              (assoc-in [app :data :eventlist] (concat (-> db (get app) :data :eventlist) (-> result :data :eventlist))))
          :kanban (-> db
              (assoc-in [app :status] :ready)
              (assoc-in [app :data :stacklist] (-> result :data :stacklist))))
        ))))

(reg-event-db
  ::error-http-result
  (fn [db [_ app result]]
    (assoc-in db [app :status] :connection-error)))
