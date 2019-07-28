(ns infonaytto.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [secretary.core :as secretary :refer-macros [defroute]]
            
            [accountant.core :as accountant]

            [reagent.core :as r]
            [re-frame.core :refer [register-handler
                                   path
                                   register-sub
                                   dispatch
                                   dispatch-sync
                                   subscribe]]
            
            [infonaytto.cal :as cal]
            [infonaytto.kanban :as kanban]
            
            [infonaytto.handlers]
            [infonaytto.subscriptions]
            [infonaytto.state]
            
            [infonaytto.views :refer [base-page]]
            
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType])
  (:import goog.History))


(enable-console-print!)

;(secretary/set-config! :prefix "#")

(def content-node
  (.getElementById js/document "app"))


; Client side routes
(defroute cal-route "/cal" []
  (dispatch [:set-current-page :cal]))
(defroute kanban-route "/kanban" []
  (dispatch [:set-current-page :kanban]))



;(defn hook-browser-navigation! []
;  (doto (History.)
;        (events/listen
;         HistoryEventType/NAVIGATE
;         (fn [event]
;           (secretary/dispatch! (.-token event))))
;        (.setEnabled true)))



(defn auto-init! []

  ; Client side navigation registering
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (secretary/dispatch! path)
      )
    :path-exists?
    (fn [path]
      (secretary/locate-route path))}
    :reload-same-path? true
   )
  (accountant/dispatch-current!)
  
  ; Init the app
  (dispatch-sync [:initialize])
  
  ; Load all app data with request dispatch
  (dispatch [:update-app-data :kanban])
  (dispatch [:update-app-data :cal])

  (r/render [base-page] content-node))



(auto-init!)