(ns infonaytto.state
  (:require
    [infonaytto.views
      :refer [kanban-view
              calendar-view]]))

        
(def initial-state
  {:routes  {:kanban     #'kanban-view
             :cal        #'calendar-view}
    
    :current-page :cal
    
    :auto true
    
    :app-info [{:tag  :kanban
                :url  "/kanban"
                :name "Kanban"}
               {:tag  :cal
                :url  "/cal"
                :name "Calendar"}]
    
    :app-static {
      :kanban {
        :graphql "/graphql/kanban"
        :query (fn [_]
          (str "{stacklist "
                 "{title order id cards "
                    "{title description id order type overdue duedate "
                     "assignedUsers{participant{displayname}} "
                     "labels{title color}}}}"))}
      :cal {
        :graphql "/graphql/cal"
        :query (fn [db]
          (str "{eventlist("
                "start:" (-> db :cal :user :start)
                " end:"  (+ (-> db :cal :user :start) (-> db :cal :user :interval))
                " ){desc summary start end loc status uid}}"))}}
   
    :kanban {:data nil
             :user {:selection nil}
             :status :empty}
    
    :cal    {:data nil
             :user {:start    (int (/ (.getTime (js/Date.)) 1000))
                    :interval (* 50 86400)}
             :status :empty}
    
})