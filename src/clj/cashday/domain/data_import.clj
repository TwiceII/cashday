(ns cashday.domain.data-import
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

;; -- Парсинг значений с csv --------------------------------------------------

(defmulti parse-value (fn [conn col-params v] (:parse col-params)))

(defmethod parse-value :date
  [conn col-params v]
  (tu/parse-to-jdate v))

(defmethod parse-value :match
  [conn col-params v]
  (if-let [match-v (get-in col-params [:mtch-params v])]
    match-v
    (throw (Exception. (str "Не найдено соответствие для " v)))))

(defmethod parse-value :double
  [conn col-params v]
  (-> v
      (cljstr/trim)
      (cljstr/replace " " "")
      (cljstr/replace "," ".")
      (Double/parseDouble)))

(defmethod parse-value :dimension
  [conn col-params v]
  (let [group-name (get-in col-params [:dim-params :name])
        dim-name (-> v (cljstr/trim))]
    (if-let [uuid (m/dimension-name->uuid dim-name group-name conn)]
      {:type :exists
       :dimension/uuid uuid}
      {:type :new
       :dimension/name dim-name
       :dim-group/name group-name})))


(defmethod parse-value :default
  [conn col-params v]
  v)

;; -- Пре-создание измерений --------------------------------------------------
(defn pre-create-dim-group-tx
  [dim-params]
  {:dim-group/uuid         (d/squuid)
   :dim-group/name         (:name dim-params)
   :dim-group/editable?    (:editable? dim-params)
   :dim-group/order-index  (:order-index dim-params)
   :dim-group/css-class    (:css-class dim-params)})


(defn line->dim-pre-txs
  [conn columns-params line-v]
  (-> {}
      ((fn [m] (assoc m :dim-params-w-idxs
                 (->> columns-params
                      (filter #(= :dimension (:parse %)))
                      (map #(assoc % :index (.indexOf columns-params %)))))))
      ((fn [m] (assoc m :pre-txs
                 (->> (:dim-params-w-idxs m)
                      (map #(-> {:dim-name  (nth line-v (:index %))
                                 :dim-group (get-in % [:dim-params :name])}))
                      (into [])))))
      :pre-txs))


(defn line->entry-pre-tx
  [conn columns-params line-v]
  (->> line-v
       (reduce (fn [[m idx] str-v]
                  (let [col-params (nth columns-params idx)
                        field (:field col-params)]
                    ;; если измерения, то коллекция
                    [(if (not (cljstr/blank? str-v))
                       (if (= :entry/dims field)
                         (update m field (fn [v]
                                           (-> v
                                               (conj (parse-value conn col-params str-v))
                                               (#(into [] %)))))
                         ;; остальные поля
                         (assoc m field (parse-value conn col-params str-v)))
                       m)
                     (inc idx)]))
               [{} 0])
       first))


(defn temp-dim-id
  "Временное строковое id для измерения"
  [dim-name dim-group]
  (str dim-name "-" dim-group))

(defn new-dim-tx
  [conn dim-name dim-group]
  (-> {:dimension/uuid (d/squuid)
       :dimension/name dim-name
       :db/id (temp-dim-id dim-name dim-group)
       :dimension/group [:dim-group/uuid (m/dim-group-name->uuid dim-group conn)]}))


(defmulti pre-dim->dim-tx (fn [pre-dim] (:type pre-dim)))

(defmethod pre-dim->dim-tx :exists
  [pre-dim]
  [:dimension/uuid (:dimension/uuid pre-dim)])

(defmethod pre-dim->dim-tx :new
  [pre-dim]
  ;; получаем временное id
  (temp-dim-id (:dimension/name pre-dim)
               (:dim-group/name pre-dim)))


;; -- Создание транзакций из пре-записей --------------------------------------
(defn entries-pre-txs->txs
  [conn pre-txs]
  (-> {}
      ;; транзакции для новых измерений
      ((fn [m]
         (assoc m :new-dim-txs
           (->> pre-txs
                (reduce (fn [all-dims pre-tx]
                          (apply conj (cons all-dims (:entry/dims pre-tx))))
                        [])
                (filter #(= :new (:type %)))
                distinct
                (map #(new-dim-tx conn (:dimension/name %) (:dim-group/name %)))))))
      ;; транзакции для записей
      ((fn [m]
         (assoc m :entries-txs
            (->> pre-txs
                 (map (fn [pre-tx] ;; конвертируем пре-запись в транзакцию
                        (-> pre-tx
                            (assoc :entry/uuid (d/squuid))
                            (update :entry/dims
                              (fn [dims]
                                (->> dims
                                     (map pre-dim->dim-tx)
                                     (into [])))))))))))
      ((fn [m]
         (assoc m :transact-result
            (concat (:new-dim-txs m) (:entries-txs m)))))
      :transact-result))


(defn process-entries
  [conn config]
  (-> {}
      ;; группы измерений для создания
      ((fn [m]
         (assoc m :dims-to-create
            (->> (:columns config)
                 (filter #(and (= :pre-create (:action %))
                               (= :dimension (:parse %))))
                 (map #(pre-create-dim-group-tx (:dim-params %)))
                 (into [])))))
      ;; добавить в БД новые группы измерений, если есть
      ((fn [m] (assoc m :transact-dim-groups
                 (if-not (u/nil-or-empty? (:dims-to-create m))
                    (do
                      (du/transact-and-return conn (:dims-to-create m))
                      :success)
                    :none))))
      ;; пред-транзакционные записи
      ((fn [m] (assoc m :entries-pre-txs
                  (csv->data (:file-name config)
                             #(line->entry-pre-tx conn (:columns config) %)))))
      ;; записи в виде транзакций
      ((fn [m] (assoc m :entries-w-dims-txs
                  (entries-pre-txs->txs conn (:entries-pre-txs m)))))
      ; добавить в БД новые записи
      ((fn [m] (assoc m :transact-entry-txs
                  (try
                    (do
                      (du/transact-and-return conn (:entries-w-dims-txs m))
                      :success)
                    (catch Exception e
                      (.getMessage e))))))
      :transact-entry-txs))


(defn process-import
  [db-uri config-uri]
  ;; создаем с нуля бд и получаем новое подключение
  (let [config (edn/read-string (slurp config-uri))
        conn (m/init-from-scratch db-uri)]
    ;; обработать записи
    (process-entries conn (first config))))
