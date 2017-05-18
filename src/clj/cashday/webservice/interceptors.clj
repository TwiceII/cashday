(ns cashday.webservice.interceptors
  "Функции для интерсепторов веб-сервиса"
  (:import (java.io ByteArrayOutputStream))
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.body-params :as http.body-params]
            [io.pedestal.interceptor :as interceptor]
            [io.pedestal.interceptor.helpers :as intrc.helpers]
            [ring.util.response :as ring-resp]
            [clojure.java.io :as io]
            [datomic.api :as d]
            [cashday.domain.model :as model]
            [cashday.domain.datomic-utils :as du]
            [cashday.common.utils :as u]
            [cognitect.transit :as transit]))

;; -- Interceptors ------------------------------------------------------------

(defn add-component-interceptors
  [service-map component]
  ; (println "add-component-interceptors")
  ; (println component)
  (println (get-in component [:datomic-db :conn]))
  (update service-map ::http/interceptors
          (fn [ic]
            (conj ic (interceptor/interceptor
                      {:name ::add-conn-component
                       :enter (fn [context]
                                ; (println "!!! entered add-comp-intc")
                                (assoc context :datomic-conn (get-in component [:datomic-db :conn])))})))))


(defn write-transit
  [x transit-form]
  (let [baos (ByteArrayOutputStream.)
        w    (transit/writer baos transit-form)
        _    (transit/write w x)
        ret  (.toString baos)]
    (.reset baos)
    ret))


(def transit-body-custom
  "Кастомный инт. для обработки результата в transit-get
   т.к. pedestal-овская ф-ия http/transit-json-body с Jetty возвращает долго результат"
  http/transit-json-body)
  ;; уже неактуально, т.к. http/transit-json-body нормально
  ;; работает при логах уровне INFO, а также кастомный метод
  ;; неправильно возвращает кодировку в некоторых символах
  ; (intrc.helpers/on-response
  ;   ::transit-body-custom
  ;   (fn [response]
  ;     (let [default-content-type "application/transit+json;charset=UTF-8"
  ;           transit-format :json
  ;           body (:body response)
  ;           content-type (get-in response [:headers "Content-Type"])]
  ;       (if (and (coll? body) (not content-type))
  ;         (-> response
  ;             (ring-resp/content-type default-content-type)
  ;             (assoc :body (write-transit body transit-format)))
  ;         response)))))


(defn check-transit-data
  "Инт. для проверки данных с транзита"
  [check-fn]
  {:name ::check-transit-data
   :enter (fn [context]
            (if-let [data (get-in context [:request :transit-params])]
              (let [errors (check-fn data)]
                (assoc-in context [:request :transit-data-check]
                                  {:valid? (u/nil-or-empty? errors)
                                   :errors errors}))
              (assoc-in context [:request :transit-data-check]
                                {:valid? false
                                 :errors ["No data was send via transit"]})))
   :leave (fn [context]
            (let [check-results (get-in context [:request :transit-data-check])]
              (assoc context :response
                (ring-resp/response (if (:valid? check-results)
                                      {:result :success}
                                      {:result :failure
                                       :errors (:errors check-results)})))))})

(defn process-transit-data
  "Инт. для обработки вход.транзит данных и возврате обраб.данных обратно"
  [process-data-fn]
  {:name ::process-transit-data
   :enter (fn [context]
            (let [conn (:datomic-conn context)]
              (if-let [data (get-in context [:request :transit-params])]
                (assoc context :processed-transit-data (process-data-fn conn data))
                context)))
   :leave (fn [context]
            (if-let [pr-data (:processed-transit-data context)]
              (assoc context :response
                (ring-resp/response {:result :success
                                     :in    (get-in context [:request :transit-params])
                                     :out   pr-data}))
              context))})


(defn transit-data->tx-data
  "Инт. для обработки данных с транзита в транзакцию для datomic"
  [to-tx-fn]
  {:name ::transit-data-to-tx
   :enter (fn [context]
            (if (get-in context [:request :transit-data-check :valid?])
              (let [conn (:datomic-conn context)]
                (assoc context ::tx-data
                  (to-tx-fn conn (get-in context [:request :transit-params]))))
              context))})


(def transact-tx
  "Инт. для выполнения заранее полученной транзакции для datomic"
  {:name ::transact-datomic-tx
   :leave (fn [context]
            (let [tx-data (::tx-data context)
                  conn    (:datomic-conn context)]
              (if (some? tx-data)
                (assoc context ::tx-result (du/transact-and-return conn tx-data))
                context)))})


(defn transit-d-transact
  "Выполнить datomic транзакцию с transit данными"
  [check-fn to-tx-fn]
  [(http.body-params/body-params)
   transit-body-custom
   (check-transit-data check-fn)
   (transit-data->tx-data to-tx-fn)
   transact-tx])


(def transit-echo
  "Получить, распечатать и вернуть transit данные
   (для дебага)"
  [(http.body-params/body-params)
   transit-body-custom
   {:name ::transit-echo
    :leave (fn [context]
              (let [transit-data (get-in context [:request :transit-params])]
                (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>")
                (println transit-data)
                (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>")
                (assoc context :response
                  (ring-resp/response {:result :success
                                       :data transit-data}))))}])


(defn transit-return
  "Получить данные в транзите и вернуть обработанные
  (без транзакции в БД)"
  [process-data-fn]
  [(http.body-params/body-params)
   transit-body-custom
   (process-transit-data process-data-fn)])


(defn transit-get
  "Получить данные в transit-е"
  [route-name-k get-data-fn]
  [transit-body-custom
   (interceptor/interceptor
    {:name route-name-k
     :leave (fn [context]
              (let [conn (:datomic-conn context)]
                (assoc context :response
                  (ring-resp/response (get-data-fn conn)))))})])



;; -- Handlers (получает request, возвращают response) ------------------------
(defn index
  [req]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"}
   :body (io/input-stream (io/resource "public/index.html"))})
