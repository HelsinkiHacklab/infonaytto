(ns infonaytto.views.layout
  (:use [hiccup.page :only (html5 include-css include-js)]))

(defn application [title pagename & content]
  (html5 {:ng-app "myApp" :lang "en"}
         [:head
          [:title title]
            (include-css "/css/app.css")
            (include-css "/css/colors.css")
          ]

          [:body
            [:div {:id "app"}]
            (include-js "/js/app.js")]
          ))




