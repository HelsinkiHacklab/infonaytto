(ns infonaytto.views
  (:require [reagent.core :as r :refer [atom]]
            
            [infonaytto.cal :as cal]
            [infonaytto.kanban :as kanban]
            
            [accountant.core :as accountant]
            [re-frame.core :refer [dispatch
                                   dispatch-sync
                                   subscribe]]))

(enable-console-print!)


(defn calendar-view []
  (println "cal view")
  (let [cal-data @(subscribe [ :cal-data ])]
    [:div
     (cal/build-cal cal-data)]))

(defn kanban-view []
  (println "kanban view")
  (let [kanban-data      @(subscribe [ :kanban-data ])]    
    [:div
     (kanban/build-kanban kanban-data)
     (kanban/build-card-popout)]))




    
(defn current-page []
  (let [page-now   @(subscribe [ :current-page ])
        routes-map @(subscribe [ :routes ]) ]
    (fn []
      [:div
       [(routes-map page-now)]]
      )))


(defn auto-btn []
  (let [auto @(subscribe [ :auto ])]
    [:button
      {:key "auto"
       :id  "auto"
       :class (if auto "btn btn-auto-on" "btn btn-auto-off")
       :on-click #(do (println "Auto") (dispatch [:auto-toggle]))}
      "Auto"]))


(defn auto-bar []
  (let [auto @(subscribe [ :auto ])
        page-now @(subscribe [ :current-page ])]
    [:div {:id "bar-bg"}
      (when auto
        (r/with-let [time-left   (r/atom 1000)
                     timer-fn    (js/setInterval #(swap! time-left dec) 100)]
            
            (when (< @time-left 0)
              (reset! time-left 1000)
              (case page-now
                :cal    (dispatch [:set-current-page :kanban])
                :kanban (dispatch [:set-current-page :cal]))
                )
            
            [:div {:id "bar-timer"
                   :style ; Animated progress bar
                     {:background (str "linear-gradient(90deg, rgba(2,0,36,1) 0%, "
                                       "rgba(255,255,255,1) "
                                       (/ @time-left 10) "%, "
                                       "rgba(255,255,255,1) 100%)")}
                   }]
        (finally (js/clearInterval timer-fn))))]))


(defn btn-draw []
  (let [app-info   @(subscribe [ :app-info ])
        page-now   @(subscribe [ :current-page ])]
    [:div {:class "btn-row"}
      (for [app app-info]
        [:button
          {:key (:name app)
           :class (if (= (:tag app) page-now) "btn btn-selected" "btn")
           :on-click #(do (println "click") (accountant/navigate! (:url app)))} ; vai dispatch?
          (:name app)])]))


(defn base-page []
    (fn []
      [:div
        [:div {:id "top"}
          [auto-bar]
          [auto-btn]
          [btn-draw]]
        [(current-page)]
      ]))
