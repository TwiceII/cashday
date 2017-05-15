(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :as repl]
            [io.pedestal.http :as http]
            [cashday.webservice.main :as webservice]
            [cashday.domain.datomic-utils :as du]
            [cashday.domain.file-data-import :as fd-import]))

(defn service-map-dev
  "Настройки веб-сервиса (dev)"
  [service-map-prod]
  (-> service-map-prod
      (assoc :env          :dev
             ::http/port   8890
             ::http/join?  false)))


(defn dev-system
  []
  (println "dev system")
  (component/system-map
    :service-map (service-map-dev webservice/service-map-prod)
    :datomic-config du/config
    :datomic-db
    (component/using
      (du/new-datomic-component)
      [:datomic-config])
    :pedestal
    (component/using
      (webservice/new-pedestal-component)
      [:service-map :datomic-db])))


(def system nil)

(defn init []
  (alter-var-root #'system
                  (constantly (dev-system))))


(defn start []
  (alter-var-root #'system component/start)
  :started)


(defn stop []
  (alter-var-root #'system
    (fn [s] (when s (component/stop s) nil))))


(defn go []
  (if system
    "System not nil, use (reset) ?"
    (do (init)
        (start))))


(defn reset []
  (stop)
  (repl/refresh :after 'user/go))


(defn init-db-import
  "Инициализация БД, загрузка данных с csv файлов"
  []
  (fd-import/init-db-import (:uri du/config)))
