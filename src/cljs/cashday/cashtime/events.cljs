(ns cashday.cashtime.events
    (:require [re-frame.core :as rfr]
              [cashday.common.reframe-utils :as rfr-u]
              [cashday.common.tuples :as tp]
              [cashday.common.random-utils :as rnd]
              [cashday.common.moment-utils :as mu]
              [cashday.common.utils :as u]
              [cashday.events :as main.events]
              [cashday.db :as db]))


;;; -----------------------------------------------------------------
;;; Сами события
;;; -----------------------------------------------------------------


;; группирование дат (по дням/месяцам/годам)
(main.events/reg-simple-set-event :cashtime/set-grouping-mode
                                  [:date-params :grouping-mode])

;; установка нового значения в поисковой строке
(main.events/reg-simple-set-event :cashtime/set-search-str
                                  [:search-dim-str])

;; установка суммы в модальном окне
(main.events/reg-simple-set-event :cashtime/set-current-v-summ
                                  [:current-entry-params :v-summ])

;; установка типа потока в модальном окне
(main.events/reg-simple-set-event :cashtime/toggle-current-v-flow
                                  [:current-entry-params :v-flow])

;; установка режима план/факт в модальном окне
(main.events/reg-simple-set-event :cashtime/toggle-current-v-type
                                  [:current-entry-params :v-type])

;; установка даты в модальном окне
(main.events/reg-simple-set-event :cashtime/set-current-date
                                  [:current-entry-params :date])

;; открытие/закрытие модального окна
(main.events/reg-simple-set-event :cashtime/toggle-modal
                                  [:show-modal?])

;; включение/отключение измерений
(rfr/reg-event-db
  :cashtime/toggle-dim-group
  (fn [db [_ dim-group]]
    (update db :active-dim-group-ids (fn [active-dims]
                                       (if (contains? active-dims (:id dim-group))
                                         (disj active-dims (:id dim-group))
                                         (conj active-dims (:id dim-group)))))))

(rfr/reg-event-db
  :cashtime/change-modal-dim-to
  (fn [db [_ dim-group-id dim-id-str]]
    (update-in db [:current-entry-params :dims]
               (fn [d]
                 (if-not (clojure.string/blank? dim-id-str)
                   (assoc d dim-group-id (UUID. dim-id-str nil))
                   (dissoc d dim-group-id))))))




;; установка новой сортировки измерений
(rfr/reg-event-db
  :cashtime/set-dim-group-sort
  [(rfr/path :sort-dim-params) rfr/trim-v]
  (fn [sort-params [dim-group-id]]
    (if (= (:group-id sort-params) dim-group-id)
      ;; обновляем только по убыванию или возрастанию
      (update sort-params :desc? not)
      ;; новое значение
      {:group-id dim-group-id :desc? false})))


;; открытие модального окна при добавлении
(rfr/reg-event-fx
  :cashtime/open-add-new-item
  main.events/default-intercs
  (fn [{:keys [db]} _]
    {:db (assoc db :show-modal? true)}))


(rfr/reg-event-fx
  :cashtime/success-post-item
  main.events/default-intercs
  (fn [{:keys [db]} [_ val]]
    (println val)
    {:dispatch [:app/load-plain-entries]
     :db (assoc db :show-modal? false)}))


;; сохранение модального окна
(rfr/reg-event-fx
  :cashtime/save-modal-item
  main.events/default-intercs
  (fn [{:keys [db]} _]
    (let [entry-params (:current-entry-params db)
          modal-params (:entry-modal-params db)
          ;; чистим конечную запись
          result-entry (-> entry-params
                           (update :dims
                                   (fn[dims]
                                     (reduce-kv (fn [m k v]
                                                  (if v (assoc m k v) m))
                                                {} dims)))
                           (update :date #(.toISOString %))
                           (update :v-summ #(js/parseFloat %)))]
      ;; TODO: перенести логику добавления/редактирования в другое место?
      (when (= (:mode modal-params) :add-item)
        {:http-xhrio (rfr-u/http-xhrio-post "/plainentries"
                                            result-entry
                                            :cashtime/success-post-item
                                            "Запись успешно добавлена"
                                            "Ошибка при добавлении записи")}))))


;; выделить ячейку
(rfr/reg-event-db
  :cashtime/select-entry-cell
  (fn [db [_ dims d flow-type]]
    (assoc db :selected-cell-params {:date d :dims dims :flow-type flow-type})))

;; при успешном удалении записей
(rfr/reg-event-fx
  :cashtime/success-delete-entries
  main.events/default-intercs
  (fn [{:keys [db]} [_ d]]
    {:dispatch [:app/load-plain-entries]}))

;; удалить запись в ячейке
(rfr/reg-event-fx
  :cashtime/delete-entry-value
  main.events/default-intercs
  (fn [{:keys [db]} [_ v-type]]
    (let [sel-cell-params (:selected-cell-params db)
          delete-params {:v-flow (:flow-type sel-cell-params)
                         :v-type v-type
                         :d-group-mode (get-in db [:date-params :grouping-mode])
                         :active-dim-groups (:active-dim-group-ids db)
                         :date (:date sel-cell-params)
                         :dims (:dims sel-cell-params)}]
      {:http-xhrio (rfr-u/http-xhrio-delete "/plainentry"
                                            delete-params
                                            :cashtime/success-delete-entries
                                            "Записи успешно удалены"
                                            "Ошибка при попытке удалить записи")})))

;; при начале перерисовки таблицы записей
(rfr/reg-event-db
  :app/entries-update-start
  main.events/default-intercs
  (fn [db _]
    (main.events/add-loading-process db :v-entries-table)))

;; при завершении отрисовки таблицы записей
(rfr/reg-event-db
  :app/entries-update-complete
  main.events/default-intercs
  (fn [db _]
    (main.events/remove-loading-process db :v-entries-table)))

(defn keyboard-events
  [db e]
  db)


; ;; случайная генерация плоских данных
; (rfr/reg-event-db
;   :cashtime/randomize-plain-entries
;   (fn [db _]
;     (assoc db :plain-entries (-> (random-plain-entries (:avail-dim-groups db)
;                                                        randomizer-params)
;                                  ;; добавляем свои для проверки
;                                  (conj {:dims {1 4 2 9 3 13}
;                                         :date "2017-07-01T00:00:00Z"
;                                         :v-type :fact
;                                         :v-flow :outflow
;                                         :v-summ 10000})
;                                  (conj {:dims {1 3 2 nil}
;                                         :date "2017-07-02T00:00:00Z"
;                                         :v-type :plan
;                                         :v-flow :inflow
;                                         :v-summ 12000})))))

; ;; параметры для рандомизации данных
; (def randomizer-params {:from-d (js/Date. 2016 2 1)
;                         ; добавляем 2 месяца от текущей
;                         :to-d (-> (js/moment.) (.add 2 "M") .toDate)
;                         :max-row-amount 10
;                         :max-vals-amount 20
;                         :max-entries 200
;                         :min-summ 100
;                         :max-summ 100000})
;
;
; (defn random-plain-entry
;   "Получить случайную плоскую запись"
;   [dim-group-options from-d to-d min-summ max-summ]
;   (let [date (rnd/random-iso-date from-d to-d)]
;     (-> {:dims (rand-nth dim-group-options)
;          :date date
;          ;:v-type (rnd/rand-nth-by-percentage {:fact 95 :plan 5})
;          :v-flow (rnd/rand-nth-by-percentage {:inflow 40 :outflow 60})
;          :v-summ (rnd/rand-from-to min-summ max-summ)}
;         ;; если дата позже текущей
;         (#(if (mu/after-today? date)
;             (assoc % :v-type :plan)
;             (assoc % :v-type (rnd/rand-nth-by-percentage {:fact 97 :plan 3})))))))
;
; (defn random-plain-entries
;   "Получить случайные плоские данные по записям"
;   [dim-groups {:keys [from-d to-d max-row-amount  min-summ max-summ max-entries]}]
;   (let [dim-group-options (-> (repeatedly (rnd/rand-1-to max-row-amount)
;                                           #(rnd/random-tuple dim-groups)))]
;     (-> (repeatedly (rnd/rand-1-to max-entries)
;                     #(random-plain-entry dim-group-options
;                                          from-d
;                                          to-d
;                                          min-summ
;                                          max-summ)))))
