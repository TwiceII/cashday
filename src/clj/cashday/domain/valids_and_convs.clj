(ns cashday.domain.valids-and-convs
  "Валидация данных и конвертирование для транзакции в БД"
  (:require [datomic.api :as d]
            [datofu.rel]
            [datofu.all]
            [clojure.string :as cljstr]
            [cashday.common.utils :as u]
            [cashday.domain.datomic-utils :as du]
            [cashday.common.time-utils :as tu]
            [cashday.domain.model :as model]))

;; -- Проверки и валидация ----------------------------------------------------
(defn check-plain-entry
  "Проверить запись на валидность и вернуть ошибки"
  [entry]
  (let [errors []]
    (-> errors
        (#(if (u/nil-or-empty? (:dims entry))
            (conj % "Введите хотя бы одно измерение")
            %))
        (#(if (zero? (:v-summ entry))
            (conj % "Введите сумму")
            %))
        (#(if (cljstr/blank? (:date entry))
            (conj % "Введите дату")
            %)))))


(defn check-rule
  [rule]
  (let [errors []]
    (-> errors
        (#(if (u/nil-or-empty? (:from rule))
            (conj % "Выберите хотя бы одно измерение источника")
            %))
        (#(if (nil? (:to rule))
            (conj % "Необходимо выбрать измерение статьи")
            %))
        (#(if (nil? (:rule-table-id rule))
            (conj % "Не передан id таблицы соответствий")
            %)))))

(defn check-dimension
  [dimension]
  (let [errors []]
    (-> errors
        (#(if (cljstr/blank? (:name dimension))
            (conj % "Необходимо ввести название")
            %))
        (#(if (nil? (:dim-group-id dimension))
            (conj % "Не передан id группы измерений")
            %)))))


(defn check-rule-delete
  "Проверить возможность удаления правила"
  [uuid]
  [])


(defn check-dimension-delete
  "Проверить возможность удаления измерения"
  [uuid]
  [])


(defn check-delete-entries
  "Проверка данных для удаления записей с ячейки"
  [entry-params]
  (let [errors []]
    (-> errors
        (#(if-not (some? (:date entry-params))
            (conj % "Не передана дата")
            %))
        (#(if-not (some? (:d-group-mode entry-params))
            (conj % "Не передан тип группировки")
            %))
        (#(if (u/nil-or-empty? (:dims entry-params))
            (conj % "Не передано ни одно измерение")
            %))
        (#(if (u/nil-or-empty? (:active-dim-groups entry-params))
            (conj % "Не передан список доступных групп измерений")
            %))
        (#(if-not (some? (:v-type entry-params))
            (conj % "Не передан тип записи (план/факт)")
            %))
        (#(if-not (some? (:v-flow entry-params))
            (conj % "Не передан тип потока (отток/приток)")
            %)))))


;; -- Получение транзакций из пришедших данных --------------------------------
(defn dimension->tx
  [conn dimension]
  [{:dimension/uuid (or (:id dimension) (d/squuid))
    :dimension/name (:name dimension)
    :dimension/group [:dim-group/uuid (:dim-group-id dimension)]}])


(defn reset-rule-from-dims-tx
  [rule]
  [:datofu.rel/reset-to-many-ref
   :rule/uuid (:id rule)
   :rule/from
   false
   :dimension/uuid
   (reduce (fn [c v]
             (conj c {:dimension/uuid v}))
           [] (:from rule))])


(defn rule->tx
  [conn rule]
  (let [from-dims (reduce (fn [coll dim-uuid]
                            (conj coll [:dimension/uuid dim-uuid]))
                          [] (:from rule))
        rule-tx {:rule/uuid (or (:id rule) (d/squuid))
                 :rule/to [:dimension/uuid (:to rule)]
                 :rule/from from-dims
                 :rule-table/_rules [:rule-table/uuid (:rule-table-id rule)]}
        txs (if (some? (:id rule))
              ;; удаляем пред. измерения исходника и добавляем новые
              [(reset-rule-from-dims-tx rule)
               [:db/add [:rule/uuid (:rule/uuid rule-tx)] :rule/to (:rule/to rule-tx)]]
              ;; просто создаем новую
              [rule-tx])]
    txs))


(defn plain-entry->tx
  "Преобразовать запись в транзакцию для datomic"
  [conn entry]
  (let [dims (reduce (fn [coll [dim-group-uuid dim-uuid]]
                        (conj coll [:dimension/uuid dim-uuid]))
                     [] (:dims entry))
        entry-tx {:entry/uuid (d/squuid)
                  :entry/summ (double (:v-summ entry))
                  :entry/v-flow (:v-flow entry)
                  :entry/v-type (:v-type entry)
                  :entry/dims dims
                  :entry/editable? true
                  :entry/date (tu/iso-w-ms->jdate (:date entry))}]
    [entry-tx]))


(defn delete-rule->tx
  [conn uuid]
  (du/retract-entity-tx :rule/uuid uuid))


(defn delete-dimension->tx
  [conn uuid]
  (du/retract-entity-tx :dimension/uuid uuid))


(defn delete-entries->txs
  [conn entry-params]
  (du/retract-entities-txs
    (model/all-entry-eids-for conn
                              (:dims entry-params)
                              (:active-dim-groups entry-params)
                              (:v-flow entry-params)
                              (:v-type entry-params)
                              (tu/parse-to-jdate (:date entry-params))
                              (:d-group-mode entry-params))))
