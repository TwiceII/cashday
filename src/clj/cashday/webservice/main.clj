(ns cashday.webservice.main
  "Основные настройки и руты веб-приложения"
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [io.pedestal.http.route.definition :refer [defroutes]]
            [io.pedestal.http.body-params :as http.body-params]
            [io.pedestal.http.route :as route]
            [cashday.webservice.interceptors :as intc]
            [cashday.domain.model :as model]
            [cashday.domain.valids-and-convs :as v-n-c]))


(def routes
  "Руты приложения"
  (route/expand-routes
    #{["/"             :get   intc/index
                       :route-name :index]

      ["/dimgroups"    :get    (intc/transit-get :dimgroups model/get-all-dim-groups)
                       :route-name :dimgroups]

      ["/dimgroups"    :post   (intc/transit-d-transact v-n-c/check-dimension
                                                        v-n-c/dimension->tx)
                       :route-name :dimgroups-post]

      ["/dimension"    :delete (intc/transit-d-transact v-n-c/check-dimension-delete
                                                        v-n-c/delete-dimension->tx)
                       :route-name :dimension-delete]

      ["/ruletables"   :get    (intc/transit-get :ruletables model/get-all-rule-tables)
                       :route-name :ruletables]

      ["/ruletables"   :post   (intc/transit-d-transact v-n-c/check-rule
                                                        v-n-c/rule->tx)
                       :route-name :ruletables-post]

      ["/rule"         :delete (intc/transit-d-transact v-n-c/check-rule-delete
                                                        v-n-c/delete-rule->tx)
                       :route-name :rule-delete]

      ["/plainentries" :get    (intc/transit-get :plainentries model/get-all-plain-entries)
                       :route-name :plainentries]

      ["/plainentries" :post   (intc/transit-d-transact v-n-c/check-plain-entry
                                                        v-n-c/plain-entry->tx)
                       :route-name :plainentries-post]

      ["/plainentry"   :delete (intc/transit-d-transact v-n-c/check-delete-entries
                                                        v-n-c/delete-entries->txs)
                       :route-name :plainentry-delete]}))


(def service-map-prod
  "Настройки веб-сервиса (продакшен)"
  {:env                  :prod
   ::http/routes         routes
   ::http/type           :jetty
   ::http/resource-path  "/public"
   ::http/secure-headers nil
   ::http/port           81})

;; настройки веб-сервиса для dev находятся в dev/user.clj




(defn start []
  "Запуск веб-сервиса для продакшена"
  (http/start (http/create-server service-map-prod)))


;; -- Interactive development in repl -----------------------------------------
; (defonce server (atom nil))
;
; (defn start-dev []
;   (reset! server
;           (http/start (http/create-server service-map-dev))))
;
;
; (defn stop-dev []
;   (http/stop @server))
;
;
; (defn restart []
;   (stop-dev)
;   (start-dev))


;; -- Component ------------------
(defn test?
  [service-map]
  (= :test (:env service-map)))




(defrecord PedestalComponent
  [service-map datomic-db server]
  component/Lifecycle
  (start [this]
    (if server
      this
      (let [new-server (-> service-map
                           (http/default-interceptors)
                           ;(update-in [::http/interceptors] (fnil vec []))
                           (intc/add-component-interceptors this)
                           (http/create-server)
                           (http/start))]
        (assoc this :server new-server))))
  (stop [this]
    (when (and server (not (test? service-map)))
      (http/stop server))
    (assoc this :server nil)))


(defn new-pedestal-component
  []
  (map->PedestalComponent {}))
