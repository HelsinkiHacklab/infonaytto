(ns infonaytto.files
  (:require [org.httpkit.client :as http]
            [clojure.java.io :as io]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zip-xml]
            [clojure.xml :as xml])
)


(defn files-list-query-gen [env]
  (hash-map
    :url (:files-url env)
    :method :propfind
    :body (str "<?xml version='1.0' encoding='UTF-8'?>
                 <d:propfind xmlns:d='DAV:'>
                   <d:prop xmlns:oc='http://owncloud.org/ns'>
                     <d:getlastmodified/>
                     <d:getcontenttype/>
                     <d:resourcetype/>
                     <d:getetag/>
                   </d:prop>
                 </d:propfind>")
     :basic-auth (:basic-auth env)
     :headers {"Content-Type" "text/xml"
               "Depth"        "1"}))

