(ns cashday.cashtime.subs
    (:require [re-frame.core :refer [reg-sub subscribe]]
              [cashday.db :refer [default-db]]
              [cashday.common.tuples :as tp]
              [cashday.common.utils :as u]
              [cashday.common.moment-utils :as mu]))
(defn dims-diff
  "Найти добавленные после правил id групп измерений"
  [d-bef d-aft]
  (let [k1 (keys d-bef)
        k2 (keys d-aft)]
    (filter #(not (u/in? k1 %)) k2)))

;; -- Функции вычисляющие значения, необходимые подпискам ---------------------
(defn current-entries-for-flow
  "Получить записи в формат.виде для потока"
  [flow-type [plain-entries
              avail-dim-groups
              avail-rule-tables
              active-dim-group-ids
              date-params
              search-dim-str
              sort-dim-params]]
  (let [search-tuple (when search-dim-str (tp/get-search-tuple-with-substr avail-dim-groups search-dim-str))
        ;; применяем к каждому измерению правила соответствий
        with-dims-entries (map (fn [pe]
                                 (let [source-dims (:dims pe)
                                       new-dims    (tp/dims->new-ruled-dims source-dims avail-rule-tables)
                                       diff-dims   (dims-diff source-dims new-dims)]
                                   (-> pe
                                       (assoc :dims new-dims)
                                       ;; доб. :ruled-dims - список добавленных из правил измерений
                                       (assoc :ruled-dims diff-dims))))
                               plain-entries)
        ;; фильтруем все плоские данные по поисковому слову
        filtered-plain-entries (tp/filter-plain-entries with-dims-entries search-tuple)
        ;; результирующий список плоских данных
        result-plain-entries filtered-plain-entries]
    (as-> result-plain-entries x
          (filter #(= (:v-flow %) flow-type) x)
          ;; конвертируем
          (tp/plain-entries->entries x
                                     (:grouping-mode date-params)
                                     active-dim-group-ids)
          ;; сортируем
          (tp/get-sorted-entries x
                                 avail-dim-groups
                                 (:group-id sort-dim-params) (:desc? sort-dim-params)))))

(defn get-used-dims-ids
  "Получить из всех записей id используемых измерений"
  [entries]
  (->> entries
       (map :tuple)
       (mapcat keys)
       distinct))


(defn distinct-dates-from-entries
  "Получить сортированный список уникальных дат из списка записей"
  [entries d-group-mode]
  (->> entries
       (map :date-values)
       (map keys)
       (reduce (fn [coll dates]
                 (apply conj (cons coll dates)))
               #{})
       (into [])
       ;; добавляем сегодняшнюю (текущую) дату, если её нет
       (#(cons (case d-group-mode
                 :by-day (mu/current-date-iso-str)
                 :by-month (mu/iso-date-by-month (mu/current-date-iso-str))
                 :by-year (mu/iso-date-by-year (mu/current-date-iso-str))
                 (mu/current-date-iso-str))
               %))
       ;; убираем если уже были повторы
       distinct
       mu/sort-dates))


(defn date-totals-from-entries
  "Получить итого по датам (по столбцу т.е.)
  возвращает вектор [дата - {:fact x :plan x}]"
  [dates entries]
  (->> dates
       (map (fn [d]
              [d (reduce (fn [m {:keys [tuple date-values]}]
                          (-> m
                              (update :fact + (or (get-in date-values [d :fact]) 0))
                              (update :plan + (or (get-in date-values [d :plan]) 0))))
                      {:fact 0 :plan 0} entries)]))))

(defn is-selected-row?
  "Проверка, что внутри строки есть выбранная ячейка"
  [sel-cell-params dims ruled-dims flow-type]
  (and (= (:dims sel-cell-params) dims)
       (= (:ruled-dims sel-cell-params) ruled-dims)
       (= (:flow-type sel-cell-params) flow-type)))


(defn row-totals-from-entries
  "Посчитать итого для каждой строки
  в виде [тапл - {:fact x :plan x}"
  [entries]
  (->> entries
       (map #(-> [(:tuple %) (reduce-kv (fn [totals k-date v-sum]
                                          (-> totals
                                              (update :fact + (or (:fact v-sum) 0))
                                              (update :plan + (or (:plan v-sum) 0))))
                                        {:fact 0 :plan 0}
                                        (:date-values %))]))))

(defn op-if-some
  "Применить какую-то операцию к значениям
   внутри fp1 fp2 по ключу key"
  [op key fp1 fp2]
  (when (or (some? (get fp1 key))
            (some? (get fp2 key)))
      (op (or (get fp1 key) 0)
          (or (get fp2 key) 0))))


(defn op-factplans
  "Сложить, вычесть два fact-plan значения"
  [op fp1 fp2]
  {:fact (op-if-some op :fact fp1 fp2)
   :plan (op-if-some op :plan fp1 fp2)})


(def -factplans (partial op-factplans -))
(def +factplans (partial op-factplans +))


(defn remains-on-end
  [inflow-totals outflow-totals visible-dates]
  (let [inflows-map   (into {} inflow-totals)
        outflows-map  (into {} outflow-totals)]
    (first (reduce (fn [[rems acc-fp] d]
                     (let [inflow-v   (get inflows-map d)
                           outflow-v  (get outflows-map d)
                           diff       (-factplans inflow-v outflow-v)
                           result     (+factplans acc-fp diff)]
                       [(conj rems [d result])
                        result]))
                   [[] {:fact 0 :plan nil}]
                   visible-dates))))


(defn remains-on-start
  [rems-on-end]
  (let [visible-dates (map first rems-on-end)]
    (first (reduce (fn [[acc i] d]
                     [(conj acc [d (if (< (dec i) 0)
                                     {:fact 0 :plan nil}
                                     (second (nth rems-on-end (dec i))))])
                      (inc i)])
                   [[] 0] visible-dates))))

;;;
;;; Сами подписки
;;;
;; -- Вложенные подписки ------------------------------------------------------
(reg-sub
  :grouping-mode
  (fn [db]
    (get-in db [:date-params :grouping-mode])))


;; -- Сложные подписки --------------------------------------------------------
(defn signals-for-computed-entries
  [query-v _]
  [(subscribe [:plain-entries])
   (subscribe [:avail-dim-groups])
   (subscribe [:avail-rule-tables])
   (subscribe [:active-dim-group-ids])
   (subscribe [:date-params])
   (subscribe [:search-dim-str])
   (subscribe [:sort-dim-params])])

(reg-sub
  :inflow-entries
  signals-for-computed-entries
  (fn [subs-v _]
    (let [e (current-entries-for-flow :inflow subs-v)]
      ; (println e)
      e)))

(reg-sub
  :outflow-entries
  signals-for-computed-entries
  (fn [subs-v _]
    (current-entries-for-flow :outflow subs-v)))


(reg-sub
  :inflow-totals
  :<- [:inflow-entries]
  (fn [inflow-entries]
    (row-totals-from-entries inflow-entries)))


(reg-sub
  :outflow-totals
  :<- [:outflow-entries]
  (fn [outflow-entries]
    (row-totals-from-entries outflow-entries)))


(reg-sub
  :visible-dates
  :<- [:outflow-entries]
  :<- [:inflow-entries]
  :<- [:grouping-mode]
  (fn [[outflow-entries inflow-entries grouping-mode]]
    (distinct-dates-from-entries (concat outflow-entries inflow-entries)
                                 grouping-mode)))


(reg-sub
  :ordered-used-dim-groups
  :<- [:outflow-entries]
  :<- [:inflow-entries]
  :<- [:avail-dim-groups]
  (fn [[outflow-entries inflow-entries avail-dim-groups]]
    (->> (concat outflow-entries inflow-entries)
         get-used-dims-ids
         (select-keys avail-dim-groups)
         vals
         (sort-by :order-index))))

(reg-sub
  :date-totals-for-outflow
  :<- [:outflow-entries]
  :<- [:visible-dates]
  (fn [[outflow-entries visible-dates]]
    (date-totals-from-entries visible-dates
                              outflow-entries)))

(reg-sub
  :date-totals-for-inflow
  :<- [:inflow-entries]
  :<- [:visible-dates]
  (fn [[inflow-entries visible-dates]]
    (date-totals-from-entries visible-dates
                              inflow-entries)))

(reg-sub
  :remains-on-end
  :<- [:date-totals-for-inflow]
  :<- [:date-totals-for-outflow]
  :<- [:visible-dates]
  (fn [[outflow-totals inflow-totals visible-dates]]
    (remains-on-end outflow-totals inflow-totals visible-dates)))


(reg-sub
  :remains-on-start
  :<- [:remains-on-end]
  (fn [remains-on-end]
    (remains-on-start remains-on-end)))


(reg-sub
  :total-remains
  :<- [:remains-on-end]
  (fn [remains-on-end]
    (->> remains-on-end
         last
         second)))

(reg-sub
  :row-sel-cell-params
  (fn [db [_ dims ruled-dims flow-type]]
    (let [sel-cell-params (:selected-cell-params db)
          row-selected?   (is-selected-row? sel-cell-params
                                            dims
                                            ruled-dims
                                            flow-type)]
      (if row-selected?
        {:selected? true
         :sel-cell-params sel-cell-params}
        ;; просто отриц. результат
        {:selected? false}))))
