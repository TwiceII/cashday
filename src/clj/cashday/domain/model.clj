(ns cashday.domain.model
  "Доменная часть данных: ф-ции для записывания/считывания с БД"
  (:require [datomic.api :as d]
            [cashday.domain.datomic-utils :as du]
            [cashday.common.time-utils :as tu]
            [datofu.all]
            [cashday.common.utils :as u]))


(def all-key-renames
  "Все возможные замены имен datomic-а на используемые на клиенте"
  {:dim-group/uuid :id
   :dim-group/name :name
   :dim-group/css-class :css-class
   :dim-group/editable? :editable?
   :dim-group/order-index :order-index
   :dimension/_group :dims
   :dimension/group :group-id
   :dimension/name :name
   :dimension/uuid :id
   :rule-table/uuid :id
   :rule-table/group-to :group-to
   :rule-table/groups-from :groups-from
   :rule-table/rules :rules
   :rule/uuid :id
   :rule/from :from
   :rule/to :to
   :entry/uuid :id
   :entry/date :date
   :entry/dims :dims
   :entry/summ :v-summ
   :entry/v-flow :v-flow
   :entry/v-type :v-type
   :entry/editable? :editable?})


(def rename-all (partial u/replace-all-keys all-key-renames))


;;-- Schemas ------------------------------------------------------------------
(def key-idents
  "Используемые ключевые слова"
  [{:db/ident :plan} {:db/ident :fact} {:db/ident :inflow} {:db/ident :outflow}])

(def dim-group-schema
  "Схема группы измерения"
  [(du/uuid-schema     :dim-group/uuid
                       "uuid группы измерения")
   (du/string-schema   :dim-group/name
                       "Название группы измерения")
   (du/boolean-schema  :dim-group/editable?
                       "Признак, можно ли редактировать группу")
   (du/string-schema   :dim-group/css-class
                       "css класс группы")
   (du/long-schema     :dim-group/order-index
                       "очередность группы начиная слева")])

(def dimension-schema
  "Схема измерения"
  [(du/uuid-schema     :dimension/uuid
                       "uuid измерения")
   (du/string-schema   :dimension/name
                       "Название измерения")
   (du/ref-schema      :dimension/group
                       "Группа, к которой относится измерение")])

(def entry-schema
  "Схема записи"
  [(du/uuid-schema     :entry/uuid
                       "uuid записи")
   (du/double-schema   :entry/summ
                       "Сумма записи")
   (du/date-schema     :entry/date
                       "Дата записи")
   (du/ref-schema      :entry/v-flow
                       "Тип потока записи (приток/отток)")
   (du/ref-schema      :entry/v-type
                       "Тип записи (факт/план)")
   (du/multiref-schema :entry/dims
                       "Измерения записи")
   (du/boolean-schema  :entry/editable?
                       "Признак, можно ли редактировать запись")])


(def rule-schema
  "Схема правила (соответствия)"
  [(du/uuid-schema     :rule/uuid
                       "uuid правила")
   (du/multiref-schema :rule/from
                       "Измерения источника (левая сторона) правила")
   (du/ref-schema      :rule/to
                       "Измерение производной (правая сторона) правила")])


(def rule-table-schema
  "Схема таблицы правил (соответствий)"
  [(du/uuid-schema     :rule-table/uuid
                       "uuid таблицы правил")
   (du/multiref-schema :rule-table/groups-from
                       "группы измерений источника")
   (du/ref-schema      :rule-table/group-to
                       "группа измерения производной")
   (du/multiref-schema :rule-table/rules
                       "правила, входящие в таблицу"
                       true)])


(defn transact-init-schemas
  "Транзактировать схемы всех сущностей"
  [conn]
  (du/transact-and-return conn (concat dim-group-schema
                                       dimension-schema
                                       entry-schema
                                       rule-schema
                                       rule-table-schema
                                       key-idents)))


; (def default-dim-groups
;   "Группы измерений по умолчанию"
;   [{:dim-group/uuid (d/squuid)
;     :dim-group/name "Контрагенты"
;     :dim-group/editable? false
;     :dim-group/css-class "dim-1"
;     :dim-group/order-index 1}
;    {:dim-group/uuid (d/squuid)
;     :dim-group/name "Договоры"
;     :dim-group/editable? false
;     :dim-group/css-class "dim-2"
;     :dim-group/order-index 2}
;    {:dim-group/uuid (d/squuid)
;     :dim-group/name "Счета"
;     :dim-group/editable? false
;     :dim-group/css-class "dim-3"
;     :dim-group/order-index 3}
;    {:dim-group/uuid (d/squuid)
;     :dim-group/name "Статьи"
;     :dim-group/editable? true
;     :dim-group/css-class "dim-4"
;     :dim-group/order-index 4}])


(def dim-group-name->uuid
  (partial du/get-uuid-by-name :dim-group/uuid :dim-group/name))


(defn dimension-name->uuid
  [dim-name dim-group-name conn]
  (->> (d/q '[:find ?dim-uuid
              :in $ ?dim-name ?dg-name
              :where
                [?e :dimension/uuid ?dim-uuid]
                [?e :dimension/name ?dim-name]
                [?e :dimension/group ?dg]
                [?dg :dim-group/name ?dg-name]]
             (d/db conn) dim-name dim-group-name)
       ffirst))


(defn create-dim-tx
  "Получить сущность измерения для транзакции"
  [dim-group-uuid dim-name]
  {:dimension/uuid (d/squuid)
   :dimension/name dim-name
   :dimension/group [:dim-group/uuid dim-group-uuid]})


(defn not-used-dim-group-ids
  "Получить uuid групп, которые не используются в dims-map"
  [dims-map active-dim-groups]
  (filter #(not (u/in? (keys dims-map) %))
          active-dim-groups))


(defn q-for-has-dims
  "Запрос для получения eids записей по параметрам"
  [dims-map active-dim-groups d-group-mode]
  (let [date-clauses (case d-group-mode
                        :by-day    ['[?e :entry/date ?date]]
                        :by-month  [['?e :entry/date '?d]
                                    '[(cashday.common.time-utils/compare-jdates ?d ?date :by-month)]]
                        :by-year   [['?e :entry/date '?d]
                                    '[(cashday.common.time-utils/compare-jdates ?d ?date :by-year)]])
        dim-equals (reduce (fn [c dim-uuid]
                             (conj c ['?e :entry/dims [:dimension/uuid dim-uuid]]))
                           [] (vals dims-map))
        dim-group-clauses (->> (not-used-dim-group-ids dims-map active-dim-groups)
                               (map #(list 'not-join '[?e]
                                            '[?e :entry/dims ?dim]
                                            ['?dim :dimension/group [:dim-group/uuid %]])))]
    (->> (apply conj [(concat [:find '(pull ?e ["*"])]
                              '[:in $ ?v-flow ?v-type ?date
                                :where [?e :entry/v-flow ?v-flow]
                                       [?e :entry/v-type ?v-type]]
                               date-clauses
                               dim-equals
                               dim-group-clauses)])
         (into []))))


(defn all-entry-eids-for
  "Получить eids всех записей, удовл. параметрам"
  [conn dims-map active-dim-groups v-flow v-type date d-group-mode]
  (->> (d/q (q-for-has-dims dims-map active-dim-groups d-group-mode)
            (d/db conn) v-flow v-type date)
       (map #(-> % first (get ":db/id")))))


; (def entry-params
;   {:v-flow :outflow,
;    :v-type :fact,
;    :d-group-mode :by-day,
;    :active-dim-groups #{#uuid "59199fa0-8693-43c7-a6ba-de21b3de8408" #uuid "59199f7e-6b6c-400b-a96d-57c39ef4c9b9"},
;    :date "2017-05-15T00:00:00Z",
;    :ruled-dims '(),
;    :dims {#uuid "59199f7e-6b6c-400b-a96d-57c39ef4c9b9" #uuid "59199f83-8989-4298-bfa6-619774f072ab"}})
;
;
; (all-entry-eids-for (d/connect du/db-uri)
;                     (u/remove-keys (:dims entry-params)
;                                    (:ruled-dims entry-params))
;                     (:active-dim-groups entry-params)
;                     (:v-flow entry-params)
;                     (:v-type entry-params)
;                     (tu/parse-to-jdate (:date entry-params))
;                     (:d-group-mode entry-params))













;; -- Нормализация для вывода сущностей ----------------------------
(defn map-norm-and-index-by-id
  [norm-fn coll]
  (->> coll
       (map norm-fn)
       (u/map-index-by :id)))


(defn norm-ident
  "Нормализовать ident (ключевое поле)"
  [ident]
  (:db/ident ident))


(defn norm-dimension
  "Нормализовать измерение"
  [dimension]
  (when dimension
    (update dimension :group-id :id)))


(defn norm-dim-group
  "Нормализовать группу измерений"
  [dim-group]
  (update dim-group :dims #(map-norm-and-index-by-id norm-dimension %)))


(defn norm-rule
  "Нормализовать правило"
  [rule groups-from]
  (-> rule
      (update :from (fn [from-dims]
                      (reduce (fn [m gr-from]
                                (assoc m (:id gr-from)
                                    (-> (u/find-some #(= (:id gr-from)
                                                         (get-in % [:group-id :id]))
                                                   from-dims)
                                        norm-dimension)))
                              {} groups-from)))
      ;#(map-norm-and-index-by-id norm-dimension %
      (update :to norm-dimension)))


(defn norm-rule-table
  "Нормализовать таблицу правил"
  [rt]
  (update rt :rules #(map-norm-and-index-by-id
                        (fn [x] (norm-rule x (:groups-from rt)))
                        %)))

(defn norm-entry
  "Нормализовать запись"
  [entry]
  (-> entry
      (update :v-type norm-ident)
      (update :v-flow norm-ident)
      (update :date tu/jdate->iso-str)
      (update :dims (fn [dims]
                      (->> dims
                           (map norm-dimension)
                           (reduce (fn [m dim]
                                     (assoc m (:group-id dim) (:id dim)))
                                   {}))))))

;; -- Основные функции по получению всех данных -------------------------------
(def dimension-pattern [:dimension/name
                        :dimension/uuid
                        {:dimension/group [:dim-group/uuid]}])

(def dim-group-basic-pattern [:dim-group/uuid :dim-group/name])

(defn default-transforms
  "Трансформации по умолчанию для списка с datomic"
  [coll]
  (->> coll
       (map first)
       rename-all))


(defn get-entities
  [conn e-uuid-key pull-pattern norm-fn index-by-id?]
  (->> (d/q (du/q-pull-pattern pull-pattern e-uuid-key)
            (d/db conn))
       default-transforms
       (#(if index-by-id?
           (map-norm-and-index-by-id norm-fn %)
           (map norm-fn %)))))


(defn get-all-dim-groups
  "Получить все группы измерений (с измерениями внутри)"
  [conn]
  (get-entities conn
                :dim-group/uuid
                [:dim-group/uuid
                 :dim-group/name
                 :dim-group/editable?
                 :dim-group/css-class
                 :dim-group/order-index
                 {:dimension/_group dimension-pattern}]
                norm-dim-group
                true))


(defn get-all-rule-tables
  "Получить все таблицы правил"
  [conn]
  (get-entities conn
                :rule-table/uuid
                [:rule-table/uuid
                 {:rule-table/groups-from dim-group-basic-pattern}
                 {:rule-table/group-to dim-group-basic-pattern}
                 {:rule-table/rules [:rule/uuid
                                     {:rule/from dimension-pattern}
                                     {:rule/to dimension-pattern}]}]
                norm-rule-table
                true))

(defn get-all-plain-entries
  "Получить все записи"
  [conn]
  (get-entities conn
                :entry/uuid
                [:entry/uuid
                 :entry/date
                 {:entry/v-flow [:db/ident]}
                 {:entry/v-type [:db/ident]}
                 :entry/summ
                 {:entry/dims dimension-pattern}]
                norm-entry
                false))


(defn init-from-scratch
  "Загрузить начальные данные: схемы и 3 группы измерения"
  [db-uri]
  ;; удаляем и создаем бд
  (d/delete-database db-uri)
  (d/create-database db-uri)
  ;; новое подключение
  (let [conn (d/connect db-uri)]
    ;; datofu функции и схемы
    (du/transact-and-return conn (datofu.all/schema-tx))
    ;; загружаем все схемы
    (transact-init-schemas conn)
    ; ;; создаем начальные группы измерений
    ; (du/transact-and-return conn default-dim-groups)
    ;; возвращаем новое подключение
    conn))

; (let [conn (d/connect du/db-uri)]
;   (du/transact-and-return conn [[:db/retractEntity 17592186045444]]))


;; -- Функции для инициализации, загрузки данных и т.д -------------------------



; datoms example
; (->> (d/datoms (du/cur-db) :eavt 17592186047100 :entry/dims)
;      (map #(-> [(.a %) (.v %)])))


; (defn get-all-plain-entries
;   "Получить все записи"
;   []
;   (let [date-from  (tu/jdate 2016 5 1)
;         date-to    (tu/jdate 2016 7 1)]
;     (->> (d/q '[:find (pull ?e [:entry/uuid
;                                 :entry/date
;                                 {:entry/v-flow [:db/ident]}
;                                 {:entry/v-type [:db/ident]}
;                                 :entry/summ
;                                 {:entry/dims [:dimension/name
;                                               :dimension/uuid
;                                               {:dimension/group [:dim-group/uuid]}]}])
;                 :in $ ?date-from ?date-to
;                 :where [?e :entry/uuid]
;                        [?e :entry/date ?cur-date]
;                        [(<= ?cur-date ?date-to)]
;                        [(> ?cur-date ?date-from)]]
;               (du/cur-db) date-from date-to)
;          default-transforms
;          (map norm-entry))))

; (q-for-has-dims {#uuid "5909cfaa-2caa-44be-ad0a-2923b4ca4ab0" #uuid "5909cfdc-bcd2-4624-ae6e-317ee78bbb6c"}
;                 #{#uuid "5909cfaa-2caa-44be-ad0a-2923b4ca4ab0"
;                   #uuid "5909cfaa-04d4-4a7d-8748-381ff21f96a4"
;                   #uuid "5909cfaa-745e-4ff9-a6c1-9697b90420e1"}
;                 :by-day)

; (def entry-params
;   {:v-flow :inflow
;    :v-type :plan
;    :d-group-mode :by-day
;    :date "2017-05-06T18:00:00Z"
;    :avail-dim-groups #{#uuid "5909cfaa-2caa-44be-ad0a-2923b4ca4ab0"
;                        #uuid "5909cfaa-04d4-4a7d-8748-381ff21f96a4"}
;                       ;  #uuid "5909cfaa-745e-4ff9-a6c1-9697b90420e1"}
;    :dims {#uuid "5909cfaa-2caa-44be-ad0a-2923b4ca4ab0" #uuid "5909cfdc-66c3-43b5-ab50-a5639995b028"}})



; (def avail-dim-groups
;   "Тестовые значения для групп измерений"
;   {1 {:id 1
;       :order-index 1 ; каким отображать по счету начиная слева
;       :name "Контрагенты"
;       :editable? false
;       :css-class "dim-1"
;       :dims {1 {:id 1 :group-id 1 :name "Казком Банк"}
;              2 {:id 2 :group-id 1 :name "Реклама.кз"}
;              3 {:id 3 :group-id 1 :name "ТOO Вектор"}
;              4 {:id 4 :group-id 1 :name "ИП Игорь Иванов"}
;              5 {:id 5 :group-id 1 :name "Асылханов М.А."}
;              6 {:id 6 :group-id 1 :name "ТОО Декор"}
;              7 {:id 7 :group-id 1 :name "Landing Ltd."}}}
;       ;; доп. для сортировки, группировки
;       ;; :sorted false
;       ;; :grouped false
;    2 {:id 2
;       :order-index 2
;       :name "Договоры"
;       :editable? false
;       :css-class "dim-2"
;       :dims {8 {:id 8 :group-id 2 :name "договор 1"}
;              9 {:id 9 :group-id 2 :name "договор 2"}
;              10 {:id 10 :group-id 2 :name "дог. от 20.03.17"}
;              11 {:id 11 :group-id 2 :name "11042015 дог."}
;              12 {:id 12 :group-id 2 :name "договор №4342"}}}
;    3 {:id 3
;       :order-index 3
;       :name "Счета"
;       :editable? true
;       :css-class "dim-3"
;       :dims {13 {:id 13 :group-id 3 :name "Qazkom"}
;              14 {:id 14 :group-id 3 :name "Основной счет"}
;              15 {:id 15 :group-id 3 :name "Валютный счет"}}}
;
;    4 {:id 4
;       :order-index 4
;       :name "Статьи"
;       :editable? true
;       :css-class "dim-4"
;       :dims {16 {:id 16 :group-id 4 :name "Статья 1"}
;              17 {:id 17 :group-id 4 :name "Статья 2"}
;              18 {:id 18 :group-id 4 :name "Статья 3"}}}
;    #uuid "590200ac-1ad2-4bcc-b099-3f3f60c48383"
;        {:id #uuid "590200ac-1ad2-4bcc-b099-3f3f60c48383"
;         :order-index 5
;         :name "Еще одно"
;         :editable? true
;         :css-class "dim-5"
;         :dims {16 {:id 16 :group-id #uuid "590200ac-1ad2-4bcc-b099-3f3f60c48383"  :name "Измерение 1"}
;                17 {:id 17 :group-id #uuid "590200ac-1ad2-4bcc-b099-3f3f60c48383" :name "Измерение 2"}
;                18 {:id 18 :group-id #uuid "590200ac-1ad2-4bcc-b099-3f3f60c48383"  :name "Измерение 3"}}}})



; (def avail-rule-tables
;   {1 {:id 1
;       :created-at 23123123
;       :groups-from [{:id 1 :name "Контрагенты"}
;                     {:id 2 :name "Договоры"}]
;       :group-to {:id 4 :name "Статьи"}
;       :rules {1 {:id 1
;                  :from {1 {:id 4 :name "ИП Игорь Иванов" :group-id 1}
;                         2 {:id 9 :name "договор 2" :group-id 2}}
;                  :to {:id 16 :name "Статья 1" :group-id 4}}
;               2 {:id 2
;                  :from {1 {:id 3 :name "ТОО Вектор" :group-id 1}
;                         2 nil}
;                  :to {:id 17 :name "Статья 2" :group-id 4}}}}})
;
;
