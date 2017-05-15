(ns cashday.core
  (:require [com.stuartsierra.component :as component]
            [cashday.webservice.main :as webservice]
            [cashday.domain.model :as model]
            [cashday.domain.file-data-import :as file-import]
            [cashday.domain.datomic-utils :as du])
  (:gen-class))


(defn prod-system
  []
  (println "prod system")
  (component/system-map
   :service-map webservice/service-map-prod
   :datomic-config du/config
   :datomic-db
   (component/using
     (du/new-datomic-component)
     [:datomic-config])
   :pedestal
   (component/using
    (webservice/new-pedestal-component)
    [:service-map :datomic-db])))


(defn -main [& args]
  (println "-main")
  (component/start (prod-system)))
