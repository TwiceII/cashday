(ns cashday.domain.file-data-import
  "Функции для загрузки начальных данных через csv"
  (:require [cashday.common.utils :as u]
            [cashday.domain.model :as m]
            [cashday.domain.datomic-utils :as du]
            [cashday.common.time-utils :as tu]
            [datomic.api :as d]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as cljstr]
            [clojure.edn :as edn]
            [com.stuartsierra.component :as component]))

(defn csv->data
  "Преобразовать csv файл в список данных"
  [file-name conv-line-fn]
  (with-open [in-file (io/reader file-name)]
        (->> (csv/read-csv in-file :separator (first ";"))
             (map conv-line-fn)
             doall)))

(defn parse-summ
  [x]
  (-> x
      (cljstr/trim)
      (cljstr/replace " " "")
      (cljstr/replace "," ".")
      (Double/parseDouble)))

(defn parse-date
  [x]
  (-> x
      (cljstr/replace " 0:00:00" "")
      tu/parse-to-jdate))

;; -- Функции получения строчки данных из строки csv --------------------------
(defn line->dim-names
  [line-v]
  (let [[_ account-str contragent-str dogovor-str _ _] line-v]
    [account-str contragent-str dogovor-str]))


(defn line->entry
  [conn line-v]
  (let [[flow-type account-str contragent-str dogovor-str summ date] line-v
        v-flow            (if (= "0" flow-type) :outflow :inflow)
        account-uuid      (m/dimension-name->uuid account-str "Счета" conn)
        contragent-uuid   (m/dimension-name->uuid contragent-str "Контрагенты" conn)
        dogovor-uuid      (if-not (cljstr/blank? dogovor-str)
                              (m/dimension-name->uuid dogovor-str "Договоры" conn)
                              :empty)
        dims (when (and (some? contragent-uuid)
                        (some? account-uuid)
                        (some? dogovor-uuid))
                (if (= :empty dogovor-uuid)
                    ;; контрагент + счет
                    [[:dimension/uuid contragent-uuid]
                     [:dimension/uuid account-uuid]]
                    ;; контрагент + счет + договор
                    [[:dimension/uuid contragent-uuid]
                     [:dimension/uuid account-uuid]
                     [:dimension/uuid dogovor-uuid]]))]
    (if (some? dims)
      {:entry/uuid (d/squuid)
       :entry/editable? false
       :entry/dims dims
       :entry/summ (parse-summ summ)
       :entry/date (parse-date date)
       :entry/v-type :fact
       :entry/v-flow v-flow
       :result :success}
      {:result :failure
       :contragent contragent-str
       :account account-str
       :dogovor dogovor-str
       :error (str (when-not (some? contragent-uuid) "Не найден контрагент")
                   (when-not (some? account-uuid) "Не найден счет")
                   (when-not (some? dogovor-uuid) "Не найден договор"))})))


(defn line->statia-dim
  [line-v]
  (let [[_ _ statia-str] line-v]
    statia-str))


(defn line->rule
  [conn line-v]
  (let [[contragent-str dogovor-str statia-str]  line-v
        contragent-uuid (m/dimension-name->uuid contragent-str "Контрагенты" conn)
        statia-uuid (m/dimension-name->uuid statia-str "Статьи" conn)
        dogovor-uuid (if-not (clojure.string/blank? dogovor-str)
                        (m/dimension-name->uuid dogovor-str "Договоры" conn)
                        :empty)
        dims (when (and (some? contragent-uuid) (some? dogovor-uuid))
                (if (= :empty dogovor-uuid)
                    [[:dimension/uuid contragent-uuid]] ; только контрагент
                    [[:dimension/uuid contragent-uuid] ; контрагент + договор
                     [:dimension/uuid dogovor-uuid]]))]
      (if (and (some? dims) (some? statia-uuid))
        {:rule/uuid (d/squuid)
         :rule/to [:dimension/uuid statia-uuid]
         :rule/from dims
        ;  :rule-table/_rules [:rule-table/uuid #uuid "5903115e-9f0d-4a48-af42-f87c5d943263"]
         :result :success}
        {:result :failure
         :contragent contragent-str
         :dogovor dogovor-str
         :statia statia-str
         :error (if (some? dogovor-uuid) "Не найден контрагент"
                                         "Не найден договор")})))


;; -- Получение данных из csv -------------------------------------------------
(defn entries-csv->dims-map
  "Получить измерения из csv файла"
  [conn file-name]
  (let [create-dim-txs (fn [name]
                         (fn [dims]
                           (->> dims
                                distinct
                                (map #(m/create-dim-tx (m/dim-group-name->uuid name conn) %)))))]
    (as-> file-name x
          (csv->data x line->dim-names)
          (reduce (fn [m [account-str contragent-str dogovor-str]]
                    (cond-> m
                            (not (cljstr/blank? account-str)) (update :accounts #(cons account-str %))
                            (not (cljstr/blank? contragent-str)) (update :contragents #(cons contragent-str %))
                            (not (cljstr/blank? dogovor-str)) (update :dogovors #(cons dogovor-str %))))
                  {} x)
          (update x :accounts (create-dim-txs "Счета"))
          (update x :contragents (create-dim-txs "Контрагенты"))
          (update x :dogovors (create-dim-txs "Договоры")))))


(defn entries-csv->entries-w-result
  "Получить записи с полем результата внутри"
  [conn file-name]
  (csv->data file-name #(line->entry conn %)))


(defn rules-csv->statia-dims
  [conn file-name]
  (as-> file-name x
        (csv->data x line->statia-dim)
        (distinct x)
        (map #(m/create-dim-tx (m/dim-group-name->uuid "Статьи" conn) %)
             x)))


(defn rules-csv->rules-w-result
  "Получить правила с полем результата внутри"
  [conn file-name]
  (csv->data file-name #(line->rule conn %)))


;; -- Обработка (добавление данных в БД) csv-файлов ---------------------------

(defn process-dims
  "Обработка измерений из csv файла"
  [conn file-name]
  (->> file-name
       (entries-csv->dims-map conn)
       (#(concat (:accounts %) (:contragents %) (:dogovors %)))
       (du/transact-and-return conn)))


(defn process-entries
  "Обработка записей из csv файла"
  [conn file-name]
  (->> file-name
       (entries-csv->entries-w-result conn)
       ((fn [es] (if-not (some #(= :failure (:result %)) es)
                   (->> es
                        (map #(dissoc % :result))
                        (du/transact-and-return conn))
                   es)))))


(defn process-statia-dims
  "Обработка измерений статей из csv файла"
  [conn file-name]
  (->> file-name
       (rules-csv->statia-dims conn)
       (du/transact-and-return conn)))


(defn process-rule-table
  "Обработать и создать таблицу правил из csv файла правил"
  [conn file-name]
  (let [rules (rules-csv->rules-w-result conn file-name)]
    (if-not (some #(= :failure (:result %)) rules)
      (->> rules
           ;; чистим правила
           (map #(dissoc % :result))
           ;; создаем новую таблицу правил
           (#(-> [{:rule-table/uuid (d/squuid)
                   :rule-table/groups-from [[:dim-group/uuid (m/dim-group-name->uuid "Контрагенты" conn)]
                                            [:dim-group/uuid (m/dim-group-name->uuid "Договоры" conn)]]
                   :rule-table/group-to [:dim-group/uuid (m/dim-group-name->uuid "Статьи" conn)]
                   :rule-table/rules %}]))
           (du/transact-and-return conn))
      (filter #(= :failure (:result %)) rules))))

;; -- Основные функции импорта данных с csv файлов ----------------------------

(defn process-entries-csv
  "Обработка всего файла entries.csv"
  [conn]
  (let [file-name "importcsv/entries.csv"
        p-dims (process-dims conn file-name)
        p-entries (process-entries conn file-name)]
    (-> {}
        (assoc :dims true)
        (assoc :entries p-entries))))

(defn process-rules-csv
  "Обработка всего файла rules.csv"
  [conn]
  (let [file-name "importcsv/rules.csv"
        p-statia-dims (process-statia-dims conn file-name)
        p-rule-table (process-rule-table conn file-name)]
    (-> {}
        (assoc :statias true)
        (assoc :rule-table p-rule-table))))


(defn import-data-from-csvs
  "Импорт данных с csv"
  [conn]
  (process-entries-csv conn)
  (process-rules-csv conn))


;; -- Метод для загрузки всех данных
(defn init-db-import
  "Загрузить данные с нуля"
  [db-uri]
  (println "data init from csvs...")
  (-> db-uri
      (m/init-from-scratch)
      (import-data-from-csvs)))
