(ns infonaytto.subscriptions
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :refer [reg-sub-raw reg-sub]]))

(reg-sub-raw
  :current-page
  (fn [db _]
    (println "reaction current-page " @db)
    (reaction (:current-page @db))))

(reg-sub-raw
  :auto
  (fn [db _]
     (println "auto " @db)
    (reaction (:auto @db))))


(reg-sub-raw
  :routes
  (fn [db _]
    (println "reaction routes " @db)
    (reaction (:routes @db))))

(reg-sub-raw
  :app-info
  (fn [db _]
    (println "reaction apps " @db)
    (reaction (:app-info @db))))

(reg-sub-raw
  :kanban-data
  (fn [db _]
    (println "reaction kanban data ")
    (reaction (-> @db :kanban :data))))

(reg-sub-raw
  :cal-data
  (fn [db _]
    (println "reaction cal data ")
    (reaction (-> @db :cal :data))))

(reg-sub
  :kanban-selection
  (fn [db _]
    (let [stack-selection (-> db :kanban :user :selection :stack)
          card-selection (-> db :kanban :user :selection :card)
          cards-data  (:cards
                        (first
                          (filter
                            #(= (:order %) stack-selection)
                            (get-in db [:kanban :data :stacklist]))))]
      (println "subs selection " (first (filter #(= (:id %) card-selection) cards-data)))
      (first (filter #(= (:id %) card-selection) cards-data)))
    ))
