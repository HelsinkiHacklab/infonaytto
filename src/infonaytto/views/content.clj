(ns infonaytto.views.content
  (:use [hiccup.form]
        [hiccup.element :only (link-to)]))

(defn index []
  [:div {:id "content"}
   [:h1 {:class "text-success"} "Hello Hiccup"]])

(defn not-found []
  [:div
   [:h1 {:class "info-warning"} "Page Not Found"]
   [:p "There's no requested page. "]
   (link-to {:class "btn btn-primary"} "/" "Take me to Home")])


(defn kanban []
  [:div {:id "content"}
   [:h1 {:class "text-success"} "Kanban"]])

(defn calendar []
  [:div {:id "content"}
   [:h1 {:class "text-success"} "Calendar"]])


(defn auto []
  [:div {:id "content"}
   [:h1 {:class "text-success"} "Auto"]])

