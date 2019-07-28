(ns infonaytto.kanban
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [reagent.core :as r]
            [markdown-to-hiccup.core :as mth]
            [cljs.core.async :refer [<!]]
            [re-frame.core :refer [dispatch dispatch-sync subscribe]]
            [cljsjs.moment]))

;(enable-console-print!)

(declare rootnode)
(def kanban-data (atom '()))


(defn wrap-urls-ahref [txt]
  [:p
   ; Look for anything that looks like an url in text and make it <a></a>
    (for [snip (re-seq #"(https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])|.*" txt)]
      (let [match (first snip)]
        (if (clojure.string/starts-with? match "http")
          (vector :span {:key (random-uuid)} " " [:a {:href match} (subs match 0 30) "..."] " ")
          (vector :span {:key (random-uuid)} match)))
    )])


(defn send-kanban-req []
  (println (str "{stacklist{title cards{title id}}}"))
  (http/post "http://localhost:3000/graphql/kanban"
            {:headers {"Content-type" "application/graphql"}
             :body (str "{stacklist {title order id cards {title description id order type overdue duedate assignedUsers{participant{displayname}} labels{title color}}}}")}))
             ;:body (str "{getstacks{title}}")}))

(declare build-kanban)


(defn render-kanban []
  (go (let [response  (<! (send-kanban-req))
            kanban  (-> response :body (js->clj) :data)]
        (reset! kanban-data kanban)
        (println (pr-str @kanban-data))
        (r/render
          (build-kanban @kanban-data)
          rootnode)
        )))


(defn only-render-kanban []
        (r/render
          (build-kanban @kanban-data)
          rootnode))


(defn update-kanban-state [state-atom state-path]
  (println "update-kanban-state")
  (println "state-atom: " @state-atom)
  (go
    (let [response  (<! (send-kanban-req))
          kanban  (-> response :body (js->clj) :data)]
         (swap! stat-atom update-in state-path kanban)
         (println "update state atom!!"))))

(defn update-kanban-data []
  (let [response  (<! (send-kanban-req))]
    (println "update async !!")
    (-> response :body (js->clj) :data)
    ))

(defn embed-kanban [k-data]
  ;(go (let [response  (<! (send-kanban-req))
  ;      kanban  (-> response :body (js->clj) :data)]
  ;      (reset! kanban-data kanban)
  ;      (println "!!")))
  ;(println "kanban-data " (pr-str @kanban-data))
  (build-kanban @kanban-data)
  )

(defn build-tags [labels users]
  [:div {:class "kanban-tags"}
    (for [label labels]
      [:span
        {:key (str "label-")
        :class "kanban-label-item"
        :style {:background-color (str "#" (:color label))}}
        (:title label)])
    (for [user users]
      [:span
        {:key (str "user-")
        :class "kanban-user-item"}
        (str "👤 " (:displayname (:participant user)))])])



(defn build-card-popout []
  (let [card @(subscribe [ :kanban-selection ])]
    (when (some? card)
      [:div {:class "kanban-popout-container"}
        [:div {:class "kanban-popout-background"
               :on-click #(dispatch [:kanban-selection nil])}]
        
        ; Popout content
        [:div
         {:key (str "card-popout-" (:id card))
          :id (str "card-popout-" (:id card))
          :class "kanban-popout-card"}
         
          [:div {:class "kanban-popout-card-title"} (:title card)]
          
          ; Description
          (when (:description card)
            [:div {:class "kanban-popout-card-content"} (->> (:description card) (mth/md->hiccup) (mth/component))])
          
          ; Tags
          (build-tags (:labels card) (:assignedUsers card)) ]])
))

(defn build-card [card stack-order]
  [:div
   {:key (str "card-" (:id card))
    :id (str "card-" (:id card))
    :class "kanban-card-item"
    :on-click #(dispatch-sync
                  [:kanban-selection
                   {:card (:id card) :stack stack-order}])}
    [:div {:class "kanban-card-title"} (:title card)]
    
    ; Description
    (when (:description card)
      [:div {:class "kanban-card-content"} (->> (:description card) (mth/md->hiccup) (mth/component))])
    
    ; Tags
    (build-tags (:labels card) (:assignedUsers card))
    ;[:div (.toLocaleString (js/Date. (:duedate calendar)))]
    ;[:div (.toLocaleString (js/Date. (* 1000 (:end calendar))))]
])

(defn build-kanban [k-data]
  ; Loop stacks
  (println "build-kanban")
  (println k-data)
  [:div
    {:class "kanban-container"
     ;:on-click #(dispatch [:kanban-selection nil])
     }
    (for [stack (:stacklist k-data)]
      [:div
       {:key (str "stack-" (:order stack))
        :id  (str "stack-" (:order stack))
        :class "kanban-stack-item"
        :style {:order (:order stack)}}
        [:span {:class "kanban-stack-title"} (:title stack)]
        
        ;cards in stacks
        (for [card (sort-by :order (:cards stack))]
          (build-card card (:order stack)))])])

;(set-validator! kanban-data (fn [x] (if (not-empty x) (only-render-kanban x) true)))
;(def rootnode (.getElementById js/document "content"))


(defn init []
  (println "kanban init")
  (def rootnode (.getElementById js/document "content"))
  (render-kanban)
)

;(set!(.-onload js/window) init)
