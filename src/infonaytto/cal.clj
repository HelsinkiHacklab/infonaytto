(ns infonaytto.cal
  (:require [org.httpkit.client :as http]
            [clojure.java.io :as io]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.xml :as xml]
            [clojure.core.async :refer [chan sliding-buffer >!!]]
            [onyx.plugin.core-async :refer [take-segments!]]
            
            [infonaytto.db :as db]
            )
  (:import (biweekly Biweekly)
           (java.time ZonedDateTime ZoneId)
           (java.time.temporal ChronoUnit WeekFields)
))


; Get default system timezone
; This is needed for recurring events handling
(def my-timezone (.getTimeZone (java.util.Calendar/getInstance)))



(defn query-gen [params]  
  (let [env        (:env params)
        start-date (:start-date params)
        end-date   (:end-date params)
        frm (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd")]
    (assoc params :query {
      :url (:cal-url env)
      :method :report
      ; using :body, in CURL this would be -data
      :body (str  "<c:calendar-query xmlns:d='DAV:' xmlns:c='urn:ietf:params:xml:ns:caldav'>"
                  "<d:prop><d:getetag /><c:calendar-data /></d:prop>"
                  "<c:filter><c:comp-filter name='VCALENDAR'><c:comp-filter name='VEVENT'>"
                  "<c:time-range  start='" (.format start-date frm) "T000000Z' end='" (.format end-date frm) "T000000Z'/>"
                  "</c:comp-filter></c:comp-filter></c:filter></c:calendar-query>")
      :basic-auth (:basic-auth env)
      :headers {"Content-Type" "text/xml"
                "Depth"        "1"}}) ))

; Get Nextcloud calendar data
(defn get-caldav-xml [params]
  ; Request for new calendar data
  (let [req (try
              @(http/request (:query params))
              (catch Exception ex 
                false ))]
    (if req
      (assoc params :success true :response (:body req))
      (assoc params :success false)) ))


; Parses caldav-xml into list of vcal calendar elements
; Input xml as string
(defn caldav-xml->vcal [params]
  (assoc params :vcal
    (map zip/node
      (map zip/down
        (-> (:response params)
            (.getBytes)
            (java.io.ByteArrayInputStream.)
            (xml/parse)
            (zip/xml-zip)
            (zip-xml/xml-> :d:response :d:propstat :d:prop :cal:calendar-data) )))))

; Make vcal list into set of calendar events
(defn vcal->calendars [params]
  (assoc params :calendars
    (->> (:vcal params)
         (apply str) ; continuous string
         (Biweekly/parse) ; parse into biweekly model
         (.all) ))) ; get all calendars from biweekly


; biweekly.ICalendars into collection of event maps with basic summary infromation
(defn calendars->rep-events [params]
  "Input biweekly.ICalendar array, outputs date sorted collection of recurring and nonrecurring events as maps"
  
  (let [limit-start-date (:start-date params)
        limit-end-date   (:end-date   params)
        calendars        (:calendars  params)]
    (letfn [
      
      ; Biweekly uses the old java.util dates and time
      ; Date handling util between util and java 8 zoned datetimes
      (instant->zoned [utildatetime]
        (ZonedDateTime/ofInstant
          (.toInstant utildatetime)
          (ZoneId/of "Europe/Helsinki")))
      (instant->timestamp [utildatetime]
        (/ (.getTime utildatetime) 1000))
      
      ; Either return single event or iterate a sample of recurrencing event occasions
      (dates-iterated [event]
        ; First check if this is a recurring event
        
        ; If this is not a recurring event, just return start date
        ; Event has either duration OR end time, use timestamps for easy arithmetics and conversions
        (if (some? (.getRecurrenceRule event))
          ; Is recurring
          
          ; Make iterated recurring dates into seq
          ; Copy duration from first occurrence
          ; Does not handle exceptions in durations then?
          (let [duration (.between ChronoUnit/MINUTES
                           (instant->zoned (.getValue (.getDateStart event)))
                           (instant->zoned (.getValue (.getDateEnd   event))))]
            
            (->>  (.getDateIterator event my-timezone)
                  (iterator-seq)
                  ; continues iteration as long as event is before end limit and stops on first negative
                  (take-while #(.isBefore (instant->zoned %) limit-end-date ))
                  ; recurring events iterate from first occurrence, filter everything before start limit
                  (filter #(.isAfter (instant->zoned %) limit-start-date))
                  (map-indexed (fn [index dur-item]
                                 (hash-map
                                   ;:recur-index index
                                   :start (instant->timestamp dur-item)
                                   :end   (+ (instant->timestamp dur-item) (* duration 60))
                                   :datetimestart (instant->zoned dur-item)
                                   :datetimeend (.plusMinutes (instant->zoned dur-item) duration))))))
          ; ---
          ; Is not recurring event, just a single occurence
          ; Wrap inside a collection so this can be used in for loop
          
          (list (hash-map :start (instant->timestamp (.getValue (.getDateStart event)))
                          :end   (instant->timestamp (.getValue (.getDateEnd event)))
                          :datetimestart (instant->zoned (.getValue (.getDateStart event)))
                          :datetimeend (instant->zoned (.getValue (.getDateEnd   event))))) ))
      
      ; Loop through calendars, its events and its iterated dates
      (loop-calendar [calendars]
        (for [calendar calendars
              event (.getEvents calendar)
              date  (dates-iterated event)]
        
        ; Return easy to use flat collection of maps
        ; There needs to be a date value
        (when (some? date)
          (hash-map
            ; Use if-let in case the value is not defined
            :type    :event
            :summary (if-let [summv (.getSummary event)] (.getValue summv) "MISSING SUMMARY")
            :desc    (if-let [descv (.getDescription event)] (.getValue descv) "")
            :status  (if-let [statv (.getStatus event)] (keyword (clojure.string/lower-case (.getValue statv))) :unknown)
            :loc     (if-let [locav (.getLocation event)] (.getValue locav) "")
            :uid     (if-let [uidv  (.getUid event)] (.getValue uidv) "")
            :start   (:start date) ; timestamp format
            :end     (:end   date)
            ; uid is not unique within recurrency group
            :recurid (if-let [recuv (.getRecurrenceId event)] (instant->timestamp (.getValue recuv)) (:start date)) ; stamps easier to compare
            :seq     (if-let [sequv (.getSequence event)] (.getValue sequv) 0)
            :datetimestart    (:datetimestart date)
            :datetimeend      (:datetimeend date))) ))
      
      ; Insert week numbers between events
      (insert-week-numbers-between [events]
        (let [weekformat (java.time.format.DateTimeFormatter/ofPattern "w")]
           (->> events
                (partition-by #(.format (:datetimestart %) weekformat))
                (map #(cons (hash-map :type :week :weeknumber (.format (:datetimestart (first %)) weekformat)) %))
                (flatten))))
      
      (exceptions-select [events]
        ; Select only those recurring events, that have max value :seq whithin their :recurid group
        ; These cases are exceptions in recurrence, seq value pointing the latest version
        (map #(apply max-key :seq (second %)) (group-by :recurid events)))
    ]
    
    ; Sort everything by date, mixing recurring and nonrecurring events from different calendars
    (->> (loop-calendar calendars)
         (exceptions-select)
         (sort-by :datetimestart)
         ;(insert-week-numbers-between)
         (assoc params :events)) )))


(defn copy-to-db [params]
  "Keep previous calendar items, add new and changed, delete old changed"
  
  (when (:success params)
    (try
      (let [selected (select-keys params [:events :start-date :end-date])
            old-cal  (set (db/keys* "cal:event:*"))]
        
        (db/set* "cal:latest" selected)
        (db/del* "cal:items")
        
        (doseq [event-data (:events selected)]
          
          (let [datetimestart (.toEpochSecond (:datetimestart event-data))
                event-identifier (str "cal:event:" (:uid event-data) ":" (:start event-data))]
          (db/zadd* "cal:items" datetimestart event-identifier)
          (db/set*  event-identifier event-data)
          ))
        
        ; Delete old items
        (let [new-cal (set (db/keys* "cal:event:*"))
              diff    (clojure.set/difference old-cal new-cal)]
          (doseq [del-item diff] (db/del* del-item) ))
        
        (assoc params :db-success true))
      (catch Exception ex (assoc params :db-success false)))
 ))


(defn get-from-db [_]
  (db/get* "cal:latest"))



; For REPL testing etc
(defn get-calendar [params]
    (->> params
         (query-gen)
         (get-caldav-xml)  ; output in string format
         (caldav-xml->vcal)
         (vcal->calendars)
         (copy-to-db)
         (calendars->rep-events)
         ;(take (:max-events params))
         (:events)
         (doall) ;?
    )
)


(defn cal-build-catalog [batch-size batch-timeout]
  [{:onyx/name   :in
    :onyx/plugin :onyx.plugin.core-async/input
    :onyx/type   :input
    :onyx/medium :core.async
    :onyx/max-peers 1
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size
    :onyx/doc "Reads segments from a core.async channel"}

   {:onyx/name :generate-query
    :onyx/fn   :infonaytto.cal/query-gen
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :get-calendar-data
    :onyx/fn   :infonaytto.cal/get-caldav-xml
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :into-vcal
    :onyx/fn   :infonaytto.cal/caldav-xml->vcal
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :into-calendars
    :onyx/fn   :infonaytto.cal/vcal->calendars
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}
   
   {:onyx/name :store-calendar
    :onyx/fn   :infonaytto.cal/copy-to-db
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}

   {:onyx/name :into-events
    :onyx/fn   :infonaytto.cal/calendars->rep-events
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}
      
   {:onyx/name :get-calendar
    :onyx/fn   :infonaytto.cal/get-from-db
    :onyx/type :function
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size}
   
   {:onyx/name   :out
    :onyx/plugin :onyx.plugin.core-async/output
    :onyx/type   :output
    :onyx/medium :core.async
    :onyx/max-peers 1
    :onyx/batch-timeout batch-timeout
    :onyx/batch-size batch-size
    :onyx/doc "Writes segments to a core.async channel"}

 ])


(def cal-workflow
  [[:in                 :generate-query]
   [:generate-query     :get-calendar-data]
   [:get-calendar-data  :into-vcal]
   [:into-vcal          :into-calendars]
   [:into-calendars     :into-events]
   [:into-events        :store-calendar]
   [:store-calendar     :out]
  ])



(def input-channel-capacity 10000)
(def output-channel-capacity (inc input-channel-capacity))

(def get-input-channel
  (memoize
   (fn [id]
     (chan input-channel-capacity))))

(def get-output-channel
  (memoize
   (fn [id]
     (chan (sliding-buffer output-channel-capacity)))))

(defn channel-id-for [lifecycles task-name]
  (:core.async/id
   (->> lifecycles
        (filter #(= task-name (:lifecycle/task %)))
        (first))))

(defn bind-inputs! [lifecycles mapping]
  (doseq [[task segments] mapping]
    (let [in-ch (get-input-channel (channel-id-for lifecycles task))]
      (doseq [segment segments]
        (>!! in-ch segment)))))

(defn collect-outputs! [lifecycles output-tasks]
  (->> output-tasks
       (map #(get-output-channel (channel-id-for lifecycles %)))
       (map #(take-segments! % 5000))
       (zipmap output-tasks)))

(defn inject-in-ch [event lifecycle]
  {:core.async/buffer (atom {})
   :core.async/chan (get-input-channel (:core.async/id lifecycle))})

(defn inject-out-ch [event lifecycle]
  {:core.async/chan (get-output-channel (:core.async/id lifecycle))})

(def in-calls
  {:lifecycle/before-task-start inject-in-ch})

(def out-calls
  {:lifecycle/before-task-start inject-out-ch})


(defn cal-build-lifecycles []
  [{:lifecycle/task :in
    :core.async/id (java.util.UUID/randomUUID)
    :lifecycle/calls :infonaytto.cal/in-calls}
   {:lifecycle/task :in
    :lifecycle/calls :onyx.plugin.core-async/reader-calls}
   
   {:lifecycle/task :out
    :lifecycle/calls :infonaytto.cal/out-calls
    :core.async/id (java.util.UUID/randomUUID)
    :lifecycle/doc "Lifecycle for writing to a core.async chan"}
   {:lifecycle/task :out
    :lifecycle/calls :onyx.plugin.core-async/writer-calls}])


(def cal-flow-conditions
  [])
