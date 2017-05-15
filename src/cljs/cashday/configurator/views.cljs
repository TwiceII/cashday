(ns cashday.configurator.views
    (:require [re-frame.core :as rfr]
              [cljsjs.semantic-ui]
              [reagent.core :as reagent]
              [reagent.impl.component :as rcomp]
              [cashday.common.views :as comm.views]
              [cashday.common.utils :as u]
              [cashday.common.tuples :as tp]
              [cashday.common.dom-utils :as dom]
              [cashday.common.moment-utils :as mu]))

(defn is-none-or-?
  [k v-to-check]
  (u/in? [:none k] v-to-check))

(defn entity-worked-on?
  [work-entity-params entity-type entity]
  (and (= (:entity-type work-entity-params) entity-type)
       (= (:entity-self work-entity-params) entity)))

(defn modes-off?
  [work-entity-params]
  (or (nil? work-entity-params)
      (= :none (:work-mode work-entity-params))))

(defn approve-current-item-edit-and-cancel-btns
  "Две кнопки для сохранения редактирования и отмены"
  []
  [comm.views/buttons-save-or-cancel-view
    (dom/no-propagation
      #(rfr/dispatch [:cfgr/approve-current-item-edit]))
    (dom/no-propagation
      #(rfr/dispatch [:cfgr/item-mode-off]))])


(defn dimension-input-view
  "Инпут для редактирования/добавления измерения"
  [dimension]
  [:div.ui.small.fluid.input dom/stop-prop-opts ; режим редактирования
    [:input.dimension-edit-input {:type "text"
                                  :autoFocus true
                                  :on-change #(rfr/dispatch [:cfgr/set-current-dim-name
                                                              (dom/value-of-input %)])
                                  :default-value (:name dimension)}]
    [:div {:style {:margin-top "3px"}}
      [:i.remove.small.right.floated.bordered.icon
        {:on-click (dom/no-propagation
                     #(rfr/dispatch [:cfgr/item-mode-off]))}]
      [:i.checkmark.green.small.right.floated.bordered.inverted.icon
        {:on-click (dom/no-propagation
                      #(rfr/dispatch [:cfgr/approve-current-item-edit]))}]]])

;;;;
;;;; Вьюшки для групп измерений
;;;;
(defn dim-row-view
  "Вьюшка для строки измерения"
  [dimension dim-group selected? in-edit?]
  [:div.item (dom/menu-item-props
                dimension
                selected?
                #(when (:editable? dim-group)
                   (rfr/dispatch [:cfgr/select-item-in-entity :dim-group dim-group dimension])))
    (if selected?
      (if in-edit?
        ;; режим редактирования
        [dimension-input-view dimension]
        ;; режим выделенной строки
        [:div
          (:name dimension)
          [:i.trash.red.small.right.floated.inverted.bordered.icon
            {:on-click
              (dom/no-propagation
                #(rfr/dispatch [:app/show-approve-modal
                                 {:text "Удалить измерение?"
                                  :approve-text "Удалить"
                                  :cancel-text "Не удалять"
                                  :approve-fn
                                    (fn [_]
                                      (rfr/dispatch [:cfgr/delete-item :dim-group
                                                                       dim-group
                                                                       dimension]))}]))}]
          [:i.pencil.green.small.right.floated.inverted.bordered.icon
            {:on-click (dom/no-propagation
                        #(rfr/dispatch [:cfgr/item-edit-mode-on]))}]])
      ;; если не выделено
      (:name dimension))])


(defn dim-group-panel-view
  "Вьюшка для группы измерений"
  [dim-group]
  (let [work-entity-params @(rfr/subscribe [:cfgr/work-entity-params])
        is-worked-on? (entity-worked-on? work-entity-params :dim-group dim-group)
        work-mode (when is-worked-on? (:work-mode work-entity-params))
        selected-item (when is-worked-on? (:selected-item work-entity-params))]
    [:div.card
      [:div.content
        [:div.header (:name dim-group)]
        [:div.meta (str "Всего: " (count (:dims dim-group)))]
        [:div.description
          [:div.ui.divided.relaxed.list.dimensions-list
            {:class (when (:editable? dim-group) "selection")}
            (for [dim (vals (:dims dim-group))]
              ^{:key (:id dim)} [dim-row-view dim
                                              dim-group
                                              (= selected-item dim)
                                              (and (= selected-item dim)
                                                   (= :edit-item work-mode))])]]]
      ;; кнопка "добавить запись"
      [:div.extra.content
        (when (and is-worked-on? (= :add-item work-mode))
          [dimension-input-view nil])
        (when (and (:editable? dim-group)
                   (modes-off? work-entity-params))
          [:div.ui.labeled.icon.tiny.positive.basic.button
            {:on-click (dom/no-propagation
                         #(rfr/dispatch [:cfgr/item-add-mode-on :dim-group dim-group]))}
            [:i.add.icon]
            "Добавить запись"])]]))


;;;;
;;;; Вьюшки для таблиц соответствий
;;;;
(defn dimension-dropdown-view
  [dim-group dim on-change-fn]
  (js/console.log "dim-group: " dim-group)
  (reagent/create-class
    {:component-did-mount #(.dropdown (dom/$find-in-rdom-node % ".ui.dropdown")
                                      #js {:onChange (fn [val text _]
                                                       (on-change-fn val))
                                           :fullTextSearch true
                                           :forceSelection false})
     :display-name "dimension-dropdown"
     :render
     (fn [this]
        (let [[dim-group dim] (dom/args-from-this this)]
          [:div.ui.fluid.small.input
            [:div.ui.fluid.search.selection.dropdown
              [:input {:type "hidden"
                       :default-value (:id dim)}]
              [:i.dropdown.icon]
              [:div.default.text (:name dim-group)]
              [:div.menu
                (for [item (-> dim-group :dims vals)]
                  ^{:key (:id item)} [:div.item {:data-value (:id item)} (:name item)])]]
            ; иконка для очищения поля
            [:i.remove.icon.link.icon-near-dropdown
             {:title "Очистить поле"
              :on-click
              #(let [el (dom/$find-in-rdom-node this ".ui.dropdown")]
                  ;; TODO: логика другая, нужно очищать значение в app-db
                  (.dropdown el "clear"))}]]))}))


(defn dropdown-for-dim-view
  "Вьюшка с дропдауном для группы измерения или конкретного измерения
  (ищется по group-id, применяется для еще невыбр. значений, н-р добавления)"
  [dim dim-group-id]
  (let [group-id (or (:group-id dim) dim-group-id)
        dim-group @(rfr/subscribe [:dim-group-by-id group-id])]
    [dimension-dropdown-view dim-group dim
                             (fn [dim-id-str]
                               (rfr/dispatch
                                 [:cfgr/set-current-rule-dim dim-group-id
                                                             dim-id-str]))]))


(defn rule-dim-td-view
  "Ячейка с измерением внутри правила"
  [dim dim-group-id in-edit? class]
  [:td (merge {:class class :title (:name dim)}
              (when in-edit?
                (merge dom/stop-prop-opts
                       {:style {:overflow "visible"}})))
    (if in-edit?
      (let [current-rule @(rfr/subscribe [:current-in-edit-item])
            current-dim (or (get-in current-rule [:from (:group-id dim)])
                            (when (= (:id dim) (get-in current-rule [:to :id]))
                              (get current-rule :to)))]
        [dropdown-for-dim-view current-dim dim-group-id])
      (:name dim))])


(defn rule-tr-view
  "Вьюшка для строки правила"
  [rule rule-table selected? in-edit?]
  [:tr (dom/menu-item-props
         rule
         selected?
         #(rfr/dispatch [:cfgr/select-item-in-entity :rule-table rule-table rule]))
    ;; измерения источника
    (for [[group-id dim] (:from rule)]
      ^{:key [group-id dim]} [rule-dim-td-view dim group-id
                                               in-edit? nil])
    ;; измерение результата
    [rule-dim-td-view (:to rule) (:group-id (:to rule))
                      in-edit? "main-dim"]
    ;; кнопки в правой колонке
    [:td
      (if selected?
        (if in-edit?
          ;; режим редактирования
          [approve-current-item-edit-and-cancel-btns]
          ;; режим просто выделенной строки
          [comm.views/buttons-edit-or-delete-view
            (dom/no-propagation
              #(rfr/dispatch [:cfgr/item-edit-mode-on]))
            (dom/no-propagation
              #(rfr/dispatch [:app/show-approve-modal
                               {:text "Удалить соответствие?"
                                :approve-text "Удалить"
                                :cancel-text "Не удалять"
                                :approve-fn
                                  (fn [_]
                                    (rfr/dispatch [:cfgr/delete-item :rule-table
                                                                     rule-table
                                                                     rule]))}]))])
        ;; хак для одинаковой высоты строк
        [:div.ui.icon.tiny.button {:style {:visibility "hidden"}} [:i.add.icon]])]])


(defn rule-table-view
  "Вьюшка для таблицы соответствий"
  [rule-table]
  (let [work-entity-params @(rfr/subscribe [:cfgr/work-entity-params])
        is-worked-on? (entity-worked-on? work-entity-params :rule-table rule-table)
        work-mode (when is-worked-on? (:work-mode work-entity-params))
        selected-item (when is-worked-on? (:selected-item work-entity-params))]
    [:div.ui.segment
      ; [:div.ui.labeled.tiny.icon.button [:i.add.icon] "Добавить измерение в таблицу"]
      [:table.ui.very.basic.celled.selectable.compact.fixed.small.table
        [:thead
          [:tr
            (for [from-g (:groups-from rule-table)]
              ^{:key (:id from-g)} [:th (:name from-g)])
            [:th.main-dim (:name (:group-to rule-table))]
            [:th {:style {:width 100}}]]]
        [:tbody
          ;; сами записи правил
          (for [rule (vals (:rules rule-table))]
            ^{:key rule} [rule-tr-view rule
                                       rule-table
                                       (= selected-item rule)
                                       (and (= selected-item rule)
                                            (= :edit-item work-mode))])
          ;; строка для новой записи
          (when (and is-worked-on? (= :add-item work-mode))
            [:tr.add-rule-tr {:on-click #(.stopPropagation %)}
              (for [from-g (:groups-from rule-table)]
                ^{:key (:id from-g)} [:td {:style {:overflow "visible"}}
                                      [dropdown-for-dim-view nil (:id from-g)]])
              [:td.main-dim
                {:style {:overflow "visible"}}
                [dropdown-for-dim-view nil (:id (:group-to rule-table))]]
              [:td
                [approve-current-item-edit-and-cancel-btns]]])]]
      ;; кнопка "добавить соответствие"
      ;; (независимо от выбр.сущности если режим редакт./добав. не включен)
      (when (modes-off? work-entity-params)
        [:div.ui.tiny.basic.icon.labeled.positive.button
          {:on-click (dom/no-propagation
                       #(rfr/dispatch [:cfgr/item-add-mode-on :rule-table rule-table]))}
          [:i.add.icon] "Добавить соответствие"])]))




;; -- Главная вьюшка -------------------------------------
(defn main-view
  []
  (let [dim-groups (vals @(rfr/subscribe [:avail-dim-groups]))
        rule-tables (vals @(rfr/subscribe [:avail-rule-tables]))]
    [:div.ui.grid
      {:on-click #(rfr/dispatch [:cfgr/reset-work-entity-params])}
      ; [:div.row
      ;   [:div.column
      ;     [:div.ui.button {:on-click #(rfr/dispatch [:app/post-transit])}
      ;                     "testing post"]]]
      [:div.row
        [:div.six.wide.column
          [:div.ui.basic.segment
            [:div.ui.left.floated.header "Измерения"]
            [:div.ui.tiny.icon.labeled.button.header-btn {:style {:visibility "hidden"}}
              [:i.add.icon] "Добавить измерение"]]
          [:div.ui.cards
            (for [dim-group dim-groups]
              ^{:key (:id dim-group)} [dim-group-panel-view dim-group])]]
        [:div.ten.wide.column
          [:div.ui.basic.segment
            [:div.ui.left.floated.header "Таблицы соответствий"]
            [:div.ui.tiny.icon.labeled.button.header-btn
              ;; скрываем временно
              {:style {:visibility "hidden"}}
              [:i.add.icon] "Добавить таблицу"]]
          (for [rule-table rule-tables]
            ^{:key (:id rule-table)} [rule-table-view rule-table])]]]))
