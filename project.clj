(defproject infonaytto "0.1.0"
  :description "Infoscreen for Nextcloud users"

  :main infonaytto.core
  :url "http://example.com/FIXME"
  
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  
  :dependencies [[org.clojure/clojure "1.10.1"]
                 
                 [org.clojure/clojurescript "1.10.520"]
                 
                 ; ETL
                 [org.onyxplatform/onyx "0.14.5"]
                 
                 ; Sheduled ETL events
                 [tea-time "1.0.1"]
                 
                 
                 [org.clojure/data.zip "0.1.3"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.csv "0.1.4"]
                 [org.clojure/data.json "0.2.6"]
                 
                 ; Sending requests
                 [http-kit "2.4.0-alpha3"]
                 
                 ; GraphQL support
                 [com.walmartlabs/lacinia "0.33.0"]

                 ; CALDAV handling
                 [net.sf.biweekly/biweekly "0.6.3"]
                 
                 ; Redis DB
                 [com.taoensso/carmine "2.19.1"]
                 
                 ; Not in use at the moment
                 [org.aarboard.nextcloud/nextcloud-api "11.0.3"]
                 
                 ; Server
                 [ring/ring-core "1.7.1"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 
                 ; React framework for clojure
                 [reagent "0.8.1"]

                 ; Application state handling
                 [re-frame "0.10.8"]
                 ; Async requests for re-frame
                 [day8.re-frame/http-fx "0.1.6"]
                 
                 ; HTML templating
                 [hiccup "1.0.5"]
                 
                 ; Server side routing
                 [compojure "1.6.1"]
                 ; Client side routing
                 [clj-commons/secretary "1.2.4"]

                 [markdown-clj "1.10.0"]
                 [markdown-to-hiccup "0.6.2"]
                 
                 [cljs-http "0.1.46"]

                 [venantius/accountant "0.2.4"] ; 

                 [cljsjs/moment "2.24.0-0"] ; time duration calculations in js
                 
                 [cljs-ajax "0.8.0"]
                 ]
  
   :aot [infonaytto.core]
   
   :plugins [[lein-ancient "0.6.15"]
             [lein-cljsbuild "1.1.7"]
             [lein-ring "0.12.5"]]
   
   :ring {:handler infonaytto.web/app
          :auto-reload? true
          :auto-refresh? true}
   
   :cljsbuild {
     :builds [{
        :source-paths ["src/cljs"]
        :compiler {
         ;; :output-to "target/cljsbuild/app.js"
          :output-to "resources/public/js/app.js"
          :optimizations :whitespace
          :pretty-print true}}]}
   

   :resources ["resources"]
   :repositories {"nextcloud-api" "https://github.com/a-schild/nextcloud-java-api"
                  "caldav4j" "https://github.com/caldav4j/caldav4j"
                  "biweekly" "https://github.com/mangstadt/biweekly"
                  }
   
   ; Stacktrace helper
   :user {:dependencies [[clj-stacktrace "0.2.8"]]
        :injections [(let [orig (ns-resolve (doto 'clojure.stacktrace require)
                                            'print-cause-trace)
                           new (ns-resolve (doto 'clj-stacktrace.repl require)
                                           'pst)]
                       (alter-var-root orig (constantly (deref new))))]}
  )
