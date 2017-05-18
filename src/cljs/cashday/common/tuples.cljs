(ns cashday.common.tuples
  (:require [cashday.common.utils :as u]
            [cashday.common.moment-utils :as mu]))

(def test-avail-dim-groups
  "Тестовые значения для групп измерений"
  {1 {:id 1
      :order-index 1 ; каким отображать по счету начиная слева
      :name "Контрагенты"
      :editable? false
      :css-class "dim-1"
      :dims {1 {:id 1 :group-id 1 :name "Халык Банк"}
             2 {:id 2 :group-id 1 :name "Реклама.кз"}
             3 {:id 3 :group-id 1 :name "ТOO Вектор"}
             4 {:id 4 :group-id 1 :name "ИП Иванов"}
             5 {:id 5 :group-id 1 :name "Асылханов М.А."}
             6 {:id 6 :group-id 1 :name "ТОО Декор"}
             7 {:id 7 :group-id 1 :name "Landing Ltd."}}}
      ;; доп. для сортировки, группировки
      ;; :sorted false
      ;; :grouped false
   2 {:id 2
      :order-index 2
      :name "Договоры"
      :editable? false
      :css-class "dim-2"
      :dims {8 {:id 8 :group-id 2 :name "договор 1"}
             9 {:id 9 :group-id 2 :name "договор 2"}
             10 {:id 10 :group-id 2 :name "дог. от 20.03.17"}
             11 {:id 11 :group-id 2 :name "11042015 дог."}
             12 {:id 12 :group-id 2 :name "договор №4342"}}}
   3 {:id 3
      :order-index 3
      :name "Счета"
      :editable? true
      :css-class "dim-3"
      :dims {13 {:id 13 :group-id 3 :name "Qazkom"}
             14 {:id 14 :group-id 3 :name "Основной счет"}
             15 {:id 15 :group-id 3 :name "Валютный счет"}}}

   4 {:id 4
      :order-index 4
      :name "Статьи"
      :editable? true
      :css-class "dim-4"
      :dims {16 {:id 16 :group-id 4 :name "Статья 1"}
             17 {:id 17 :group-id 4 :name "Статья 2"}
             18 {:id 18 :group-id 4 :name "Статья 3"}}}})


;;    5 {:id 5
;;       :order-index 5
;;       :name "Еще измерение"
;;       :css-class "dim-5"
;;       :dims {1 {:id 1 :name "Значение 1"}}}

(def test-rule-tables
  {1 {:id 1
      :created-at 23123123
      :groups-from [{:id 1 :name "Контрагенты"}
                    {:id 2 :name "Договоры"}]
      :group-to {:id 4 :name "Статьи"}
      :rules {1 {:id 1
                 :from {1 {:id 4 :name "ИП Иванов" :group-id 1}
                        2 {:id 9 :name "договор 2" :group-id 2}}
                 :to {:id 16 :name "Статья 1" :group-id 4}}
              2 {:id 2
                 :from {1 {:id 3 :name "ТОО Вектор" :group-id 1}
                        2 nil}
                 :to {:id 17 :name "Статья 2" :group-id 4}}}}})

(defn inc-max
  [seq]
  (if (not (u/nil-or-empty? seq))
    ((fnil inc 0)(apply max seq))
    1))


(defn accept-new-rule-locally
  "Добавить новое или редактировать существ. соотв-ие локально в таблицу"
  [current-rule rule-table]
  (println "accept-new-rule-locally")
  (.log js/console current-rule)
  (.log js/console rule-table)
  (if (some? (:id current-rule)) ; если уже есть, заменяем
    (assoc-in rule-table [:rules (:id current-rule)] current-rule)
    ;; если новое
    (let [new-id (random-uuid)
          new-rule (assoc current-rule :id new-id)]
      (assoc-in rule-table [:rules new-id] new-rule))))

(defn accept-new-dimension-locally
  "Добавить новое или редактировать существ. измерение локально в группу"
  [current-dim dim-group]
  (println "accept-new-dimension-locally")
  (if (some? (:id current-dim)) ; если уже есть, заменяем
    (assoc-in dim-group [:dims (:id current-dim)] current-dim)
    ;; если новое
    (let [new-id (random-uuid)
          new-dim (assoc current-dim :id new-id
                                     :group-id (:id dim-group))]
      (assoc-in dim-group [:dims new-id] new-dim))))


(defn remove-rule-locally
  "Удалить соотв-ие из таблицы локально"
  [rule rule-table]
  (update rule-table :rules #(dissoc % (:id rule))))

; (accept-new-rule-locally {:id nil
;                           :from {1 {:id 5 :name "Асылханов М.А." :group-id 1}
;                                  2 nil}
;                           :to {:id 17 :name "Статья 2" :group-id 4}}
;                          (first test-rule-tables))


(defn tuples-from-entries
  "Получить список всех таплов измерений внутри записей"
  [entries]
  (mapv :tuple entries))

(defn pair-from-tuple
  [tuple group-id]
  (when (and group-id tuple)
    (if-let [dim-id (get tuple group-id)]
      [group-id dim-id])))

(defn update-plain-entry-date-for-group
  "Обновить дату внутри плоских данных чтобы сгруппировать по дням/месяцам/годам и т.д."
  [plain-entry d-group-mode]
  (case d-group-mode
    :by-month (assoc plain-entry :date (mu/iso-date-by-month (:date plain-entry)))
    :by-year (assoc plain-entry :date (mu/iso-date-by-year (:date plain-entry)))
    :by-day plain-entry
    plain-entry))

(defn update-plain-entry-dims-for-avails
  "Обновить измерения внутри плоских данных чтобы сгруппировать по доступным измерениям"
  [plain-entry active-dim-group-ids]
  (update plain-entry :dims (fn [t] (select-keys t active-dim-group-ids))))

(defn plain-entries->entries
  "Конвертировать плоские данные по записям в форматированные"
  [plain-entries d-group-mode active-dim-group-ids]
  (->> plain-entries
       (map #(-> %
                 (update-plain-entry-date-for-group d-group-mode)
                 (update-plain-entry-dims-for-avails active-dim-group-ids)))
       (reduce (fn [m e]
                 (if-not (u/nil-or-empty? (:dims e))
                   (-> m
                       (update-in [(:dims e) :d-values (:date e) (:v-type e)]
                                  + (or (:v-summ e) 0))
                       ;; если есть измерения, созданные правилами
                       (assoc-in [(:dims e) :ruled-dims] (:ruled-dims e)))
                   m))
               {})
       (reduce-kv (fn [vc tuple vals]
                   (conj vc {:tuple tuple
                             :ruled-dims (:ruled-dims vals)
                             :date-values (:d-values vals)}))
                [])))

;; (plain-entries->entries test-plain-entries :by-month [2])


(defn tuple-w-ids->tuple-w-names
  "Таплы с id измерений в таплы с названиями измерений"
  [all-dim-groups tuple-w-ids]
  (->> tuple-w-ids
     (reduce-kv (fn [m k v]
                  (assoc m k (-> (get-in all-dim-groups [k :dims v])
                                 :name)))
                {})))

;; (tuple-w-ids->tuple-w-names test-dim-groups {1 2, 2 3})
;; => {1 "Визитки", 2 "Bestprofi.kz"}

(defn sort-tuples
  "Сортировать список таплов по названиям в измерении"
  [tuples dim-groups sort-gr-id desc?]
  (-> {}
      (assoc :tuples-w-ids tuples)
      (#(assoc % :tuples-w-names-map (reduce (fn[m tuple]
                                              (assoc m tuple (tuple-w-ids->tuple-w-names dim-groups tuple)))
                                         {} (:tuples-w-ids %))))
      (#(assoc % :sorted-tuples-w-names (->> (:tuples-w-names-map %)
                                             vals
                                             (sort (fn[t1 t2]
                                                     ((if desc? > <)
                                                      (or (get t1 sort-gr-id) "")
                                                      (or (get t2 sort-gr-id) "")))))))
      (#(assoc % :sorted-tuples-w-ids (->> (:sorted-tuples-w-names %)
                                           (map (fn[t](u/k-of-v (:tuples-w-names-map %) t))))))
      :sorted-tuples-w-ids))


(defn get-sorted-entries
  "Получить отсортированные записи по названиям измерений в нужной группе"
  [entries dim-groups sort-gr-id desc?]
  (if sort-gr-id
    (-> entries
        tuples-from-entries
        (sort-tuples dim-groups sort-gr-id desc?)
        u/indexed-hashmap-of-coll
        ((fn[ind-hm] (sort-by :tuple (fn[t1 t2]
                                       (< (get ind-hm t1) (get ind-hm t2)))
                              entries))))
    entries))

;; (get-sorted-entries [{:tuple {1 4, 3 1}
;;                 :date-values {"2017-07-01T00:00:00Z" {:fact 10000 :plan 5000}
;;                               "2017-06-08T00:00:00Z" {:fact 8000 :plan nil}
;;                               "2017-07-03T00:00:00Z" {:fact 18000 :plan 2000}}}
;;                {:tuple {1 2, 2 3}
;;                 :date-values {"2017-06-05T00:00:00Z" {:fact 4000 :plan -15000}}}]
;;               test-dim-groups
;;               1 false)

(defn get-search-tuple-with-substr
  "Получить хмэп вида {id-группы [id-измерений]}, у которых есть в названиях подстрока"
  [dim-groups ss]
  ;; (get-search-tuple-with-substr test-dim-groups "го")
  ;; => {1 (4 7), 2 (4)}
  (->> dim-groups
       vals
       (reduce (fn [m dg]
                 (let [ids (->> dg
                                :dims
                                vals
                                (filter #(u/has-substr? (:name %) ss))
                                (map :id))]
                   (if-not (u/nil-or-empty? ids) (assoc m (:id dg) ids)
                     m)))
               {})))


(defn pair-in-search-tuple?
  "Находится ли пара в тапле для поиска
  пример: (pair-in-search-tuples? [1 7] {1 [4 7], 2 [4]}) - true"
  [pair search-tuples]
  (when pair
    (boolean (u/in? (get search-tuples (first pair))
                    (second pair)))))

(defn tuple-in-search-tuple?
  "Содержится ли в тапле хоть одна пара удовл. таплу поиска"
  [tuple search-tuple]
  (->> tuple
       vec
       (some #(pair-in-search-tuple? % search-tuple))
       boolean))

(defn filter-plain-entries
  "Отфильтровать (если нужно) плоские данные"
  [plain-entries search-tuple]
  (->> plain-entries
       ;; если нужно фильтровать по поисковой строке
       ((fn[pe](if search-tuple
                 (filter #(tuple-in-search-tuple? (:dims %) search-tuple) pe)
                 pe)))))

;; (def test-plain-entries
;;   "Записи в негруп.виде"
;;   [{:dims {1 2}
;;     :date "2017-07-01T00:00:00Z"
;;     :v-type :fact
;;     :v-flow :outflow
;;     :v-summ 10000}
;;    {:dims {1 2, 2 1}
;;     :date "2017-07-01T00:00:00Z"
;;     :v-type :plan
;;     :v-flow :inflow
;;     :v-summ 5000}
;;    {:dims {1 2, 2 3}
;;     :date "2017-07-07T00:00:00Z"
;;     :v-type :fact
;;     :v-flow :inflow
;;     :v-summ 25000}
;;    {:dims {1 2, 2 3}
;;     :date "2017-02-01T00:00:00Z"
;;     :v-type :plan
;;     :v-flow :outflow
;;     :v-summ 1111}
;;    {:dims {1 3, 2 3}
;;     :date "2017-02-03T00:00:00Z"
;;     :v-type :fact
;;     :v-flow :outflow
;;     :v-summ 3333}
;;    {:dims {2 1}
;;     :date "2017-07-07T00:00:00Z"
;;     :v-type :fact
;;     :v-flow :outflow
;;     :v-summ 2200}
;;    ])


;; (def test-entries
;;   [{:tuple {1 2, 2 3}
;;     :date-values {"2017-07-01T00:00:00Z" {:fact 10000 :plan 5000}
;;                   "2017-06-08T00:00:00Z" {:fact 8000 :plan nil}
;;                   "2017-07-03T00:00:00Z" {:fact 18000 :plan 2000}}}
;;    {:tuple {1 3, 3 1}
;;     :date-values {"2017-06-05T00:00:00Z" {:fact 4000 :plan -15000}}}])



; ;; --------------------------------
; (def test-source {1 4, 2 9, 3 13})
; (def rule-table (first (vals a-rule-tables)))
; ;; --------------------------------



;; -- Функции для конвертирования измерений с учетом правил -------------------

(defn from-dim-group-ids
  "Получить ids всех групп измерений источников из таблицы правил"
  [rule-table]
  (->> rule-table
       :groups-from
       (map :id)))


(defn rt-fits-dims?
  "Проверяет, применима ли таблица правил к измерениям источника"
  [dims rule-table]
  (let [src-dgr-ids (-> dims keys)
        from-dgr-ids (from-dim-group-ids rule-table)]
        ;; to-измерение правила нет в источнике
    (and (not (u/in? src-dgr-ids (get-in rule-table [:group-to :id])))
        ;  ;; все from-измерения правила есть в источнике
        ;  (u/coll-contains-subcoll? src-dgr-ids from-dgr-ids))))
        ;; хотя бы одно измерение источника должно быть в from-измерениях правила)))
        (some #(u/in? from-dgr-ids %) src-dgr-ids))))

; (rt-fits-dims? {1 2}  {:id 1
;                        :groups-from [{:id 1}
;                                      {:id 2}]
;                        :group-to {:id 3}})


(defn rule->from-map
  [rule]
  (reduce-kv (fn [m k v] (assoc m k (:id v)))
             {} (:from rule)))


(defn dims->cutted-map
  "Получить из измерений источника только те, к-ые есть в from-измерениях правил"
  [dims from-dgr-ids]
  (reduce (fn [m from-id]
            (assoc m from-id (get dims from-id)))
          {} from-dgr-ids))

  ; (select-keys dims from-dgr-ids))

; (dims->cutted-map {1 2, 4 6} [1 2])
; => {1 2, 2 nil}


(defn rule-is-for-dims?
  "Проверяет, подходит ли правило для источника
  from-dgr-ids - ids from-измерений правила"
  [dims rule from-dgr-ids]
  (= (rule->from-map rule)
     (dims->cutted-map dims from-dgr-ids)))


(defn find-rule-for-dims
  "Найти нужное правило в таблице для источника"
  [dims rule-table]
  (let [from-ids (from-dim-group-ids rule-table)]
    (u/find-some (fn [rule]
                   (rule-is-for-dims? dims rule from-ids))
                 (-> rule-table :rules vals))))


(defn apply-rule-to-dims
  "Применить правило к источнику (добавить to-измерение)"
  [dims rule]
  ;; предполагается, что до этого все проверки сделаны
  (assoc dims (get-in rule [:to :group-id])
              (get-in rule [:to :id])))


;; простой вариант с возвращением значения
(defn try-rule-table-to-dims
  "Попробовать применить таблицу правил к источнику"
  [dims rule-table]
  (if (rt-fits-dims? dims rule-table)
    (if-let [found-rule (find-rule-for-dims dims rule-table)]
      (apply-rule-to-dims dims found-rule)
      dims)
    dims))

;; вариант с возвращением информации
(defn try-rule-table-to-dims-w-info
  "Попробовать применить таблицу правил к источнику
   вернуть подробный результат"
  [dims rule-table]
  (-> {:rt-id (:id rule-table)
       :source-dims dims}
      (assoc :rt-fits? (rt-fits-dims? dims rule-table))
      ((fn [info]
         (assoc info :rt-found-rule (when (:rt-fits? info)
                                      (find-rule-for-dims dims rule-table)))))
      ((fn [info]
         (assoc info :result-dims (if-let [rule (:rt-found-rule info)]
                                    (apply-rule-to-dims dims rule)
                                    dims))))))

; (try-rule-table-to-dims test-source rule-table)
; (try-rule-table-to-dims-w-info test-source rule-table)


(defn dims->new-ruled-dims
  "Получить из хм измерений и правил новый хм измерений"
  [dims rule-tables-map]
  (if-not (u/nil-or-empty? rule-tables-map)
    (as-> {} x
          (assoc x :in-dims dims)
          (assoc x :rt-transforms
            (reduce (fn [result-infos rt]
                      (let [info (try-rule-table-to-dims-w-info dims rt)]
                        (conj result-infos info)))
                   [] (vals rule-tables-map)))
          (assoc x :out-dims (-> x :rt-transforms last :result-dims))
          ;; возвращаем только результат
          (:out-dims x))
    dims))
