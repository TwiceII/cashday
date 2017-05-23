(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :as repl]
            [io.pedestal.http :as http]
            [cashday.webservice.main :as webservice]
            [cashday.domain.datomic-utils :as du]
            [cashday.domain.model :as m]
            [cashday.domain.file-data-import :as fd-import]
            [cashday.domain.data-import :as d-imp]
            [clojure.edn :as edn]))


(defn get-project-config
  "Получить настройки проекта"
  []
  (edn/read-string (slurp "config/project-config.edn")))


(defn service-map-dev
  "Настройки веб-сервиса (dev)"
  [service-map-prod port]
  (-> service-map-prod
      (assoc :env          :dev
             ::http/port   port
             ::http/join?  false)))


(defn dev-system
  []
  (println "dev system")
  (let [config (get-project-config)]
    (component/system-map
      :service-map (service-map-dev webservice/service-map-prod
                                    (:dev-web-port config))
      :datomic-config {:uri (:db-uri config)}
      :datomic-db
      (component/using
        (du/new-datomic-component)
        [:datomic-config])
      :pedestal
      (component/using
        (webservice/new-pedestal-component)
        [:service-map :datomic-db]))))


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
  (let [config (get-project-config)]
    (d-imp/process-import (:db-uri config)
                          (:fileupload-config config))))


(defn init-db-from-scratch
  "Инициализация БД с нуля"
  []
  (let [config (get-project-config)]
    (m/init-from-scratch (:db-uri config))))
