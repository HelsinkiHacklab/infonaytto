(ns infonaytto.cal
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs-http.client :as http]
            [reagent.core :as r]
            [cljs.core.async :refer [<!]]
            [cljsjs.moment]
            [re-frame.core :refer [dispatch]]))

;(enable-console-print!)

(def min-timestamp (atom (int (/ (.getTime (js/Date.)) 1000))))
(def max-timestamp (atom (int (/ (.getTime (js/Date.)) 1000))))
(declare rootnode)
(def cal-data (atom '()))

(def load-interval-secs (* 50 86400)) ; 50 days


(def color-class
  ["border--navy" "border--blue" "border--aqua" "border--teal" "border--olive" "border--green"
   "border--lime" "border--yellow" "border--orange" "border--red" "border--fuchsia" "border--purple"
   "border--maroon" "border--white" "border--gray" "border--silver" "border--black"])


;(defn time-diff-str [start end]
;  (let [startm (js/moment start "X")
;        endm   (js/moment end "X")]
;    ; Should this be displayed in hours or in days
;    (if (not= (.dayOfYear startm) (.dayOfYear endm))
;      (str (.format startm "dd D.M") " - " (.format endm "dd D.M") )
;      (str (.format startm "dd D.M H:mm") " - " (.format endm "H:mm") ))
;))

(defn time-diff-str [start end]
  (let [startm (js/moment start "X")
        endm   (js/moment end "X")]
    ; Should this be displayed in hours or in days
    (let [dddd  (str (.format startm "dddd "))
          dddd2 (str (.format endm "dddd "))
          D     (str (.format startm "D. "))
          D2    (str (.format endm "D. "))
          Hmm   (str (.format startm "H:mm - ") (.format endm "H:mm "))]
    (if (= (.dayOfYear startm) (.dayOfYear endm))
      [:span [:span {:class "cal-day"} dddd] [:span {:class "cal-date"} D] [:span {:class "cal-time"} Hmm]]
      [:span [:span {:class "cal-day"} dddd] [:span {:class "cal-date"} D] " - " [:span {:class "cal-day"} dddd2] [:span {:class "cal-date"} D2]])
)))

(defn wrap-urls-ahref [txt]
   ; Look for anything that looks like an url in text and make it <a></a>
    (for [snip (re-seq #"(https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])|.*" txt)]
      (let [match (first snip)]
        (if (clojure.string/starts-with? match "http")
          (vector :span {:key (random-uuid)} " " [:a {:href match} (subs match 0 30) "..."] " ")
          (vector :span {:key (random-uuid)} match)))
    ))


(defn send-cal-req [time-start time-end]
  (println (str "{eventlist(start:" time-start  " end:" time-end "){desc summary start end loc status uid}}"))
    (http/post "http://localhost:3000/graphql/cal"
              {:headers {"Content-type" "application/graphql"}
               :body (str "{eventlist(start:" time-start  " end:" time-end "){desc summary start end loc status uid}}")}))

(declare render-cal)

(defn get-cal []
  (println "get-calendar")
  (go (let [time-start @max-timestamp
            time-end   (+ @max-timestamp load-interval-secs)
            response   (<! (send-cal-req time-start time-end))
            resp-cal   (-> response :body :data :eventlist)
            min-start  (apply #(min (:start %)) calendar)
            max-end    (apply #(max (:end %)) calendar)]
        (when (:success response) (reset! max-timestamp time-end))
        (reset! cal-data (concat @cal-data resp-cal))
        (render-cal @cal-data)
        )))


(defn insert-months [low high]
  (let [this_year (.getFullYear (js/Date.))]
    (map (fn [x] (hash-map :start (dec (/ (.getTime x) 1000)) :month (.toLocaleString x "en-us" #js {:month "long" })));  ;(.getMonth x)))
      (filter
        #(< low (/ (.getTime %) 1000) high)
        (for [y (range this_year (+ this_year 3)) m (range 0 12)] (js/Date. y m 1) )))
  ))

(defn render-cal [c-data]
  (println "render-cal >>" (pr-str (.getElementById js/document "content")) "<<")
  (r/render (build-cal c-data) (.getElementById js/document "content"))
)

(defn build-event [e-data]
  [:div
    {:key (str (:uid e-data) "-" (:start e-data))
     :class (str
       "cal-item "
       
       ; cancelled or not?
       (case (:status e-data)
         :tentative "cal-status-tentative "
         :cancelled "cal-status-cancelled "
         "cal-status-confirmed ")
       
       ; set border color
       ; Idea: take ten first letters of event title, make into any number
       ; Use the number to pick a color mod amount of colors
       (get color-class (mod (hash (subs (:summary e-data) 0 10)) 17))
       )
     :style {
       :order (:start e-data)
       :width (+ 200 (count (:desc e-data)))
       }}
   (if (= :cancelled (:status e-data))
     [:span {:class "cal-event-timedate"} "cancelled"]
     [:span {:class "cal-event-timedate"} (time-diff-str (:start e-data) (:end e-data))])
   [:span {:class "cal-event-summary"} (:summary e-data)]
   [:span {:class "cal-event-description"} (wrap-urls-ahref (:desc e-data))]
   [:span {:class "cal-event-location"} (str "– " (:loc e-data))]
   ;[:div (.toLocaleString (js/Date. (* 1000 (:start calendar))))]
   ;[:div (.toLocaleString (js/Date. (* 1000 (:end calendar))))]
   ])


(defn build-cal [c-data]
  (println "build-calendar")
  (let [eventlist        (:eventlist c-data)
        months           (insert-months (apply min (map :start eventlist)) (apply max (map :start eventlist)))
        month-event-list (concat eventlist months)]
    [:div {:class "cal-container"}
      
      ; Loop events
      (for [cal-item month-event-list]
        (if (:month cal-item)
          ; Month marker
           [:div {:key (str "month-" (:start cal-item))
              :class "cal-month"
              :style {
                :order (:start cal-item)}}
            [:span {:class "cal-month-name"} (:month cal-item)]]
           
          ; Event item
          [build-event cal-item]
        ))
        ; Load more button
        [:div {:id "cal-more-item"
               :style {:order "9999999999"}}
          [:button {:on-click #(dispatch [:update-app-data :cal])
                    :class "cal-button"} "▶"]]
    ]
  )
)


(defn init []
  (def rootnode (.getElementById js/document "content"))
  (get-cal)
)

;(set!(.-onload js/window) init)
