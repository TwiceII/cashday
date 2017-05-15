(ns cashday.domain.datomic-utils
  "Функции и утилиты для работы с Datomic"
  (:require [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clj-time.coerce :as ct]))

;; -- Настройки ---------------------------------------------------------------
(def db-uri "datomic:sql://cashday-finish?jdbc:postgresql://localhost:5432/cashday-finish?user=datomic&password=datomic")

(def config {:uri db-uri})


;; -- Служебные функции -------------------------------------------------------
(defn transact-and-return
  "Выполнить транзакцию и вернуть результат"
  [conn txs]
  (println "txs: ")
  (println txs)
  (deref (d/transact conn txs)))



;; -- Хэлперы функции ---------------------------------------------------------
(defn q-by-uuid [attrs]
  "Получить динамический datalog запрос"
  (apply conj '[:find] (list 'pull '?e attrs)
              '[:in $ ?name-k ?name-v
                :where [?e ?name-k ?name-v]]))

(defn get-uuid-by-name
  "Получить uuid сущности по какому-то полю имени"
  [uuid-k name-k name-v conn]
  (-> (d/q (q-by-uuid [uuid-k])
           (d/db conn) name-k name-v)
      ffirst
      uuid-k))

(defn q-pull-pattern
  [pattern where-attr]
  (apply conj [[:find] (list 'pull '?e pattern)
                :where ['?e where-attr]]))

(defn retract-entity-tx
  "Получить транзакцию для удаления сущности по uuid"
  [e-uuid-k e-uuid-v]
  [[:db/retractEntity [e-uuid-k e-uuid-v]]])

(defn retract-entities-txs
  "Получить транзакции на удаление (ретракт) по id сущностей"
  [eids]
  (for [eid eids]
    [:db/retractEntity eid]))


;; -- Для создания схемы ------------------------------------------------------
(defn uuid-schema
  "Получить мэп с параметрами для uuid поля"
  [ident-key doc-name]
  {:db/ident ident-key
   :db/doc doc-name
   :db/valueType :db.type/uuid
   :db/cardinality :db.cardinality/one
   :db/unique :db.unique/identity})

(defn string-schema
  "Получить мэп с параметрами для string поля"
  [ident-key doc-name]
  {:db/ident ident-key
   :db/doc doc-name
   :db/valueType :db.type/string
   :db/fulltext true
   :db/cardinality :db.cardinality/one})

(defn date-schema
  "Получить мэп с параметрами для string поля"
  [ident-key doc-name]
  {:db/ident ident-key
   :db/doc doc-name
   :db/valueType :db.type/instant
   :db/cardinality :db.cardinality/one
   :db/index true})

(defn double-schema
  "Получить мэп с параметрами для double поля"
  [ident-key doc-name]
  {:db/ident ident-key
   :db/doc doc-name
   :db/valueType :db.type/double
   :db/cardinality :db.cardinality/one})

(defn ref-schema
  "Получить мэп с параметрами для ref поля (внешний ключ либо ident для ключа)"
  [ident-key doc-name]
  {:db/ident ident-key
   :db/doc doc-name
   :db/valueType :db.type/ref
   :db/cardinality :db.cardinality/one})

(defn multiref-schema
  "Получить мэп с параметрами для ref поля (может быть несколько)
   (внешние ключи либо identы для ключа)"
  ([ident-key doc-name]
   {:db/ident ident-key
    :db/doc doc-name
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/many})
  ([ident-key doc-name isComponent?]
   {:db/ident ident-key
    :db/doc doc-name
    :db/valueType :db.type/ref
    :db/isComponent true
    :db/cardinality :db.cardinality/many}))

(defn boolean-schema
  "Получить мэп с параметрами для boolean поля"
  [ident-key doc-name]
  {:db/ident ident-key
   :db/doc doc-name
   :db/valueType :db.type/boolean
   :db/cardinality :db.cardinality/one})

(defn long-schema
  "Получить мэп с параметрами для integer (long) поля"
  [ident-key doc-name]
  {:db/ident ident-key
   :db/doc doc-name
   :db/valueType :db.type/long
   :db/cardinality :db.cardinality/one})




(defrecord DatomicComponent
  [datomic-config conn tx-report-queue]
  component/Lifecycle
  (start [component]
    (println "creating DatomicComponent")
    (println datomic-config)
    (let [url (:uri datomic-config)
          ; deleted? (d/delete-database url)
          created? (d/create-database url)
          conn (d/connect url)
          tx-report-queue (d/tx-report-queue conn)
          component (assoc component :conn conn :tx-report-queue tx-report-queue)]
      component))
  (stop [component]
    (d/release conn)
    (assoc component :conn nil)))

(defn new-datomic-component []
  (map->DatomicComponent {}))
