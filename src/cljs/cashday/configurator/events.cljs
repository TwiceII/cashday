(ns cashday.configurator.events
    (:require [re-frame.core :as rfr]
              [cashday.common.reframe-utils :as rfr-u]
              [cashday.events :as main.events]
              [cashday.common.tuples :as tp]))

;;;;
;;;; Вспомог. функции
;;;;
(def entity-type->item-name
  {:rule-table "Соответствие"
   :dim-group "Измерение"})

(def work-type->name
  {:edit-item "отредактировано"
   :add-item "добавлено"
   :delete-item "удалено"})

(defn get-toastr-success-msg
  "Получить сообщения для оповещения"
  [entity-type work-type]
  (str (get entity-type->item-name entity-type)
       " "
       (get work-type->name work-type)))


(defn default-current-edit-item
  "Получить пустое (для добавления) значение элемента сущности"
  [entity-type entity-self]
  (case entity-type
    :rule-table {:id nil
                 :from (reduce (fn [m gr-from]
                                 (assoc m (:id gr-from) nil))
                               {} (:groups-from entity-self))
                 :to nil}
    :dim-group {:id nil :name nil}))


;;;;
;;;; Сами события
;;;;

;; снять все выделения и отключить режимы
(rfr/reg-event-db
  :cfgr/reset-work-entity-params
  (fn [db _]
    (assoc db :cfgr/work-entity-params nil)))


;; выделить щелкнутый элемент в сущности (измерение в группе или правило в таблице)
(rfr/reg-event-db
  :cfgr/select-item-in-entity
  (fn [db [_ entity-type entity-self entity-item]]
    (assoc db :cfgr/work-entity-params {:entity-type entity-type
                                        :entity-self entity-self
                                        :work-mode :none
                                        :selected-item entity-item
                                        :current-in-edit-item entity-item})))


;; установить значение дропдауна для редакт.измерения
(rfr/reg-event-db
  :cfgr/set-current-rule-dim
  (fn [db [_ dim-group-id dim-id]]
    (let [dim-id (UUID. dim-id nil)]
      ;; TODO: доделать норм.проверки и исключения
      (let [dimension (get-in db [:avail-dim-groups dim-group-id :dims dim-id])]
        ;; если есть в списке :from, иначе в :to
        (if (contains? (get-in db [:cfgr/work-entity-params
                                   :current-in-edit-item
                                   :from]) dim-group-id)
          (assoc-in db [:cfgr/work-entity-params
                        :current-in-edit-item
                        :from dim-group-id] dimension)
          (assoc-in db [:cfgr/work-entity-params
                        :current-in-edit-item
                        :to] dimension))))))


;; включить режим добавления нового элемента в сущность
(rfr/reg-event-db
  :cfgr/item-add-mode-on
  main.events/default-intercs
  (fn [db [_ entity-type entity-self]]
    (assoc db :cfgr/work-entity-params
                {:entity-type entity-type
                 :entity-self entity-self
                 :work-mode :add-item
                 :selected-item nil
                 :current-in-edit-item (default-current-edit-item entity-type
                                                                  entity-self)})))
;; включить режим редактирования элемента
;; (т.к. предполагается, что его вкл. можно только при выделении элемента,
;; то никаких проверок не делается)
(rfr/reg-event-db
  :cfgr/item-edit-mode-on
  main.events/default-intercs
  (fn [db _]
    (assoc-in db [:cfgr/work-entity-params :work-mode] :edit-item)))

;; отключить режимы редактирования или добавления элемента
;; (т.к. предполагается, что его вкл. можно только при выделении элемента,
;; то никаких проверок не делается)
(rfr/reg-event-db
  :cfgr/item-mode-off
  main.events/default-intercs
  (fn [db _]
    (assoc-in db [:cfgr/work-entity-params :work-mode] :none)))


(defn get-result-rule
  [rt-id item]
  {:rule-table-id rt-id
   :id (:id item)
   :from (reduce-kv (fn [coll k v]
                      (if (some? v)
                        (conj coll (:id v))
                        coll))
                    [] (:from item))
   :to (get-in item [:to :id])})

(defn get-result-dimension
  [dim-group-id item]
  {:dim-group-id dim-group-id
   :id (:id item)
   :name (:name item)})

;; при успешном добавлении/редактировании/удалении измерения или правила
(rfr/reg-event-fx
  :cfgr/success-xhrio-item
  (fn [{:keys [db]} [_ val]]
    (let [work-entity-params (:cfgr/work-entity-params db)
          entity-type (:entity-type work-entity-params)
          load-event (case entity-type :rule-table [:app/load-avail-rule-tables]
                                       :dim-group  [:app/load-avail-dim-groups])]
      (js/console.log "success-val")
      (js/console.log val)
      {:dispatch-n [load-event
                    [:cfgr/reset-work-entity-params]]})))

;; подтвердить результат редактирования/добавления элемента
(rfr/reg-event-fx
  :cfgr/approve-current-item-edit
  main.events/default-intercs
  (fn [{:keys [db]} _]
    (let [work-entity-params (:cfgr/work-entity-params db)
          entity-type (:entity-type work-entity-params)
          entity-self (:entity-self work-entity-params)
          current-item (:current-in-edit-item work-entity-params)
          post-link (case entity-type :rule-table "/rules"
                                      :dim-group "/dimensions")
          result-item ((case entity-type
                        :rule-table get-result-rule
                        :dim-group get-result-dimension) (:id entity-self)
                                                         current-item)]
      {:http-xhrio (rfr-u/http-xhrio-post post-link
                                          result-item
                                          :cfgr/success-xhrio-item
                                          (get-toastr-success-msg
                                            entity-type
                                            (:work-mode work-entity-params))
                                          "Не удалось выполнить операцию")})))


;; удаление элемента из сущности
(rfr/reg-event-fx
  :cfgr/delete-item
  (fn [{:keys [db]} [_ entity-type entity-self entity-item]]
    (let [work-entity-params (:cfgr/work-entity-params db)
          entity-type (:entity-type work-entity-params)
          delete-link (case entity-type :rule-table "/rule"
                                        :dim-group  "/dimension")]
      {:http-xhrio (rfr-u/http-xhrio-delete delete-link
                                            (:id entity-item)
                                            :cfgr/success-xhrio-item
                                            (get-toastr-success-msg
                                              entity-type
                                              :delete-item))})))


(rfr/reg-event-db
  :cfgr/set-current-dim-name
  main.events/default-intercs
  (fn [db [_ value]]
    (assoc-in db [:cfgr/work-entity-params :current-in-edit-item :name] value)))


(defn keyboard-events
  [db e]
  (if (= :edit-item (get-in db [:cfgr/work-entity-params :work-mode]))
     {:db (assoc-in db [:cfgr/work-entity-params :work-mode] :none)}
     {:dispatch [:cfgr/reset-work-entity-params]}))


;; -- CRUD групп измерений ----------------------------------------------------
;; включить режим добавления новой группы
(rfr/reg-event-db
  :cfgr/set-add-dim-group-mode
  main.events/default-intercs
  (fn [db _]
    (assoc db :cfgr/work-entity-params {:entity-type :dim-group
                                        :current-in-edit-entity {:name ""}
                                        :work-mode :add-group
                                        :entity-self nil
                                        :selected-item nil
                                        :current-in-edit-item nil})))

;; включить режим редактирования группы
(rfr/reg-event-db
  :cfgr/set-edit-dim-group-mode
  main.events/default-intercs
  (fn [db [_ dim-group]]
    (assoc db :cfgr/work-entity-params {:entity-type :dim-group
                                        :current-in-edit-entity {:id (:id dim-group)
                                                                 :name (:name dim-group)}
                                        :work-mode :edit-group
                                        :entity-self dim-group
                                        :selected-item nil
                                        :current-in-edit-item nil})))

;; редактировать название текущей выделенной группы
(rfr/reg-event-db
  :cfgr/set-current-dim-group-name
  main.events/default-intercs
  (fn [db [_ value]]
    (assoc-in db [:cfgr/work-entity-params :current-in-edit-entity :name] value)))

;; подтвердить добавление/редактирование группы
(rfr/reg-event-fx
  :cfgr/approve-current-dim-group-edit
  main.events/default-intercs
  (fn [{:keys [db]} _]
    (let [work-entity-params (:cfgr/work-entity-params db)
          current-entity (:current-in-edit-entity work-entity-params)]
      {:http-xhrio (rfr-u/http-xhrio-post "/dimgroups"
                                          current-entity
                                          :cfgr/success-xhrio-dim-group
                                          (case (:work-mode work-entity-params)
                                            :add-group "Группа измерений добавлена"
                                            :edit-group "Группа измерений отредактирована")
                                          "Не удалось выполнить операцию")})))

;; при успешном добавлении/редактировании/удалении группы
(rfr/reg-event-fx
  :cfgr/success-xhrio-dim-group
  main.events/default-intercs
  (fn [{:keys [db]} [_ val]]
    {:dispatch-n [[:app/load-avail-dim-groups]
                  [:cfgr/reset-work-entity-params]]}))


;; удаление группы
(rfr/reg-event-fx
  :cfgr/delete-dim-group
  main.events/default-intercs
  (fn [{:keys [db]} [_ dim-group]]
    {:http-xhrio (rfr-u/http-xhrio-delete "/dimgroup"
                                          (:id dim-group)
                                          :cfgr/success-xhrio-dim-group
                                          "Группа удалена")}))
