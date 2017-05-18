(ns cashday.cashtime.views
    (:require [re-frame.core :refer [subscribe dispatch dispatch-sync]]
              [reagent.core :as reagent]
              [reagent.impl.component :as rcomp]
              [cashday.common.utils :as u]
              [cashday.common.tuples :as tp]
              [cashday.common.dom-utils :as dom]
              [cashday.common.moment-utils :as mu]
              [cashday.common.reframe-utils :as rfr-u]))


;; -- Функции для получения данных для вьюшек ----------------------------------------------------
(defn pair->dim
  "Получить из пары нужное измерение"
  [avail-dim-groups pair]
  (let [group-id (first pair)
        dim-id (second pair)]
    (get-in avail-dim-groups [group-id :dims dim-id])))


(defn is-current-date?
  "Проверка, что дата является текущей (с учетом группировки по дням/месяцам/годам)"
  [d d-group-mode]
  (let [current-iso-str-d (mu/current-date-iso-str)]
    (case d-group-mode
      :by-day (= d current-iso-str-d)
      :by-month (and (= (.year (js/moment. d)) (.year (js/moment. current-iso-str-d)))
                     (= (.month (js/moment. d)) (.month (js/moment. current-iso-str-d))))
      :by-year (= (.year (js/moment. d)) (.year (js/moment. current-iso-str-d)))
      false)))

(defn is-selected-cell?
  "Проверка, что ячейка записи является выбранной"
  [sel-cell-params d dims ruled-dims flow-type]
  (and (= (:date sel-cell-params) d)
       (= (:dims sel-cell-params) dims)
       (= (:ruled-dims sel-cell-params) ruled-dims)
       (= (:flow-type sel-cell-params) flow-type)))


;; -- Измерения (левая часть таблицы) -----------------------------------------
(defn dim-td
  "Ячейка с измерением"
  [dim-group dim]
  [:td {:class (when dim (:css-class dim-group))
        :title (:name dim)}
   (:name dim)])


(defn tuple-tr
  "Строка названий конкретных измерений"
  [ordered-dim-groups tuple]
  (let [avail-dim-groups @(subscribe [:avail-dim-groups])]
    [:tr
     (for [dim-group ordered-dim-groups]
       (let [dim-group-id (:id dim-group)
             pair (tp/pair-from-tuple tuple dim-group-id)]
         ^{:key [dim-group-id pair]}[dim-td dim-group (pair->dim avail-dim-groups pair)]))]))


(defn dim-group-header
  "Заголовок для группы измерений"
  [dim-group sort-dim-params]
  (let [gr-id (:id dim-group)]
    [:th {:class (when (= (:group-id sort-dim-params) gr-id)
                   (str "sorted " (if (:desc? sort-dim-params) "descending" "ascending")))
          :on-click #(dispatch [:cashtime/set-dim-group-sort gr-id])}
     (:name dim-group)]))


(defn dimensions-panel
  "Измерения (левая часть таблицы)"
  []
  (let [outflow-tuples (tp/tuples-from-entries @(subscribe [:outflow-entries]))
        inflow-tuples (tp/tuples-from-entries @(subscribe [:inflow-entries]))
        sort-dim-params @(subscribe [:sort-dim-params])
        ordered-dim-groups @(subscribe [:ordered-used-dim-groups])]
    [:div
     [:table.ui.very.basic.sortable.single.line.fixed.compact.small.table
      [:thead
       [:tr
        (for [dim-group ordered-dim-groups]
          ^{:key (:id dim-group)} [dim-group-header dim-group sort-dim-params])]]
      [:tbody
       ;; измерения притоков
       (for [i-tuple inflow-tuples]
         ^{:key i-tuple} [tuple-tr ordered-dim-groups i-tuple])
       ;; итого притоков
       [:tr
        [:td.total-cell {:colSpan (count ordered-dim-groups)
                         :style {:font-weight "bold"}} "Итого поступлений"]]
       ;; измерения оттоков
       (for [o-tuple outflow-tuples]
         ^{:key o-tuple} [tuple-tr ordered-dim-groups o-tuple])
       ;; итого оттоков
       [:tr
        [:td.total-cell {:colSpan (count ordered-dim-groups)
                         :style {:font-weight "bold"}} "Итого выплат"]]
       ;; остатки на начало
       [:tr
        [:td.total-cell {:colSpan (count ordered-dim-groups)
                         :style {:font-weight "bold"}} "Остаток на начало"]]
       ;; остатки на конец
       [:tr
        [:td.total-cell {:colSpan (count ordered-dim-groups)
                         :style {:font-weight "bold"}} "Остаток на конец"]]]]]))


;; -- Сами записи с датами (центр.часть таблицы) -------------------------------------
(defn date-header-th
  "Заголовок для даты"
  [date current-d? d-group-mode]
  [:th {:class (when current-d? "today-cell")}
   (mu/print-date-in-needed-format date d-group-mode)])


(defn date-headers-tr
  "Строка с заголовками для дат"
  [dates d-group-mode]
  [:tr
   (for [d dates]
     ^{:key d} [date-header-th d (is-current-date? d d-group-mode) d-group-mode])])


(defn entry-value
  "Вьюшка для вывода суммы записи
  deletable? - возможность удаления"
  [value flow-type selected? deletable?]
  (let [delete-fn  (fn [type]
                     (dom/no-propagation
                      #(dispatch [:app/show-approve-modal
                                  {:text "Удалить запись?"
                                   :approve-text "Удалить"
                                   :cancel-text "Не удалять"
                                   :approve-fn
                                    (fn [_]
                                      (dispatch [:cashtime/delete-entry-value type]))}])))
        delete-plan-fn (delete-fn :plan)
        delete-fact-fn (delete-fn :fact)]
      (fn [value flow-type selected? deletable?]
        (if (not (nil? value))
          [:div.value-cell
           (when (:fact value)
             [:span.fact-value
              {:class (case flow-type
                        :inflow "positive"
                        :outflow "negative"
                        "")}
              (u/money-str-with-zero (:fact value))
              (when (and selected? deletable?)
               [:i.link.remove.icon.delete-entry-icon
                {:title "Удалить запись"
                 :on-click delete-fact-fn}])])
           (when (and (:plan value) (not= 0 (:plan value)))
             [:span.plan-value
              (u/money-str-with-zero (:plan value))
              (when (and selected? deletable?)
               [:i.link.remove.icon.delete-entry-icon
                {:title "Удалить запись"
                 :on-click delete-plan-fn}])])]
          [:div.value-cell " "]))))



(defn value-cell-td
  "Ячейка со значением"
  [dims ruled-dims d value flow-type current-d? selected?]
  (let [on-click-fn #(do
                       (dispatch [:cashtime/select-entry-cell dims ruled-dims d flow-type])
                       (.stopPropagation %))]
    (fn [dims ruled-dims d value flow-type current-d? selected?]
      ; (println "update value-cell")
      [:td {:class (str (when current-d? "today-cell ")
                        (when selected?  "selected-cell "))
            :on-click on-click-fn}
       [entry-value value flow-type selected? true]])))


(defn values-row-tr
  "Строка со значениями"
  [dims ruled-dims dates date-values flow-type d-group-mode]
  (let [row-sel-cell-params @(subscribe [:row-sel-cell-params dims ruled-dims flow-type])]
    ; (println "update values-row")
    [:tr
     (for [d dates]
       ^{:key d} [value-cell-td dims
                                ruled-dims
                                d
                                (get date-values d)
                                flow-type
                                (is-current-date? d d-group-mode)
                                (if (:selected? row-sel-cell-params)
                                  (is-selected-cell? (:sel-cell-params row-sel-cell-params)
                                                     d
                                                     dims
                                                     ruled-dims
                                                     flow-type)
                                  false)])]))


(defn date-total-td
  "Итого по столбцу (дате)"
  [date-total flow-type current-d?]
  [:td.total-cell {:class (when current-d? "today-cell")}
   [entry-value date-total flow-type false false]])


(defn entries-w-dates
  "Вьюшка для записей и дат"
  []
  (reagent/create-class
    {:component-did-mount #(do
                            (dispatch [:app/entries-update-complete]))
     :component-will-update #(do
                              (dispatch-sync [:app/entries-update-start]))

     :component-did-update #(do
                             (dispatch [:app/entries-update-complete]))
     :reagent-render
      (fn []
        (let [outflow-entries      @(subscribe [:outflow-entries])
              inflow-entries       @(subscribe [:inflow-entries])
              d-group-mode         @(subscribe [:grouping-mode])
              dates                @(subscribe [:visible-dates])
              total-outflows       @(subscribe [:date-totals-for-outflow])
              total-inflows        @(subscribe [:date-totals-for-inflow])
              remains-on-start     @(subscribe [:remains-on-start])
              remains-on-end       @(subscribe [:remains-on-end])
              rows-fn (fn [flow-kw flow-entries]
                        (for [{:keys [tuple ruled-dims date-values]} flow-entries]
                          ^{:key tuple} [values-row-tr tuple
                                                       ruled-dims
                                                       dates
                                                       date-values
                                                       flow-kw
                                                       d-group-mode]))

              total-fn (fn [flow-kw totals]
                         (for [dt totals]
                           ^{:key (first dt)} [date-total-td (second dt)
                                                             flow-kw
                                                             (is-current-date? (first dt) d-group-mode)]))]
          [:table.ui.very.basic.celled.single.line.compact.small.table
      ;;      {:style {:width "1200px"}}
           [:thead
            ;; заголовки с датами
            [date-headers-tr dates d-group-mode]]
           [:tbody
            ;; строки притоков
            (rows-fn :inflow inflow-entries)
            ;; строка итого притоков
            [:tr (total-fn :inflow total-inflows)]
            ;; строки оттоков
            (rows-fn :outflow outflow-entries)
            ;; строка итого оттоков
            [:tr (total-fn :outflow total-outflows)]
            ;; строка остатки на начало
            [:tr (for [r remains-on-start]
                   ^{:key (first r)} [date-total-td (second r)
                                                    false
                                                    (is-current-date? (first r) d-group-mode)])]
            ;; строка остатки на конец
            [:tr (for [r remains-on-end]
                   ^{:key (first r)} [date-total-td (second r)
                                                    false
                                                    (is-current-date? (first r) d-group-mode)])]]]))}))



;; -- Итого (правая часть таблицы) -------------------------------------

(defn row-total-tr
  "Итого по строке"
  [total flow-type]
  [:tr [:td.total-cell [entry-value total flow-type false false]]])


(defn row-totals
  "Вьюшка по итогам по строкам"
  []
  (let [outflow-totals    @(subscribe [:outflow-totals])
        inflow-totals     @(subscribe [:inflow-totals])
        total-remains     @(subscribe [:total-remains])
        rows-totals-fn    (fn [flow-kw flow-totals]
                            (for [e flow-totals]
                               ^{:key (first e)} [row-total-tr (second e) flow-kw]))
        flow-total-fn     (fn [flow-kw flow-totals]
                            [:td.total-cell
                             [entry-value (reduce (fn [m [tpl v]]
                                                    (-> m
                                                        (update :fact + (:fact v))
                                                        (update :plan + (:plan v))))
                                                  {:fact 0 :plan 0} flow-totals)
                                          flow-kw false false]])]
    [:table.ui.very.basic.compact.small.table
     [:thead
      [:tr
       [:th "Итого"]]]
     [:tbody
      ;; строки итого притоков
      (rows-totals-fn :inflow inflow-totals)
      ;; итого по всем притокам
      [:tr (flow-total-fn :inflow inflow-totals)]
      ;; строки итого оттоков
      (rows-totals-fn :outflow outflow-totals)
      ;; итого по всем оттокам
      [:tr (flow-total-fn :outflow outflow-totals)]
      ;; итого остатки на начало
      [:tr [:td.total-cell [entry-value total-remains false false false]]]
      ;; итого остатки на конец
      [:tr [:td.total-cell [entry-value total-remains false false false]]]]]))


;; -- Остальные вьюшки -------------------------------------
(defn dimension-toggler-item
  [dim-group active?]
  [:div.item
   [:div.ui.checkbox {:class (when active? "checked")
                      :on-click #(dispatch [:cashtime/toggle-dim-group dim-group])}
    [:input {:type "checkbox"
             :checked active?}]
    [:label (:name dim-group)]]])


(defn dimensions-toggler
  "Переключатель активных измерений"
  []
  (let [avail-dim-groups       @(subscribe [:avail-dim-groups])
        active-dim-group-ids   @(subscribe [:active-dim-group-ids])]
    [:div.ui.horizontal.list
     (for [[_ dim-group] avail-dim-groups]
       ^{:key (:id dim-group)} [dimension-toggler-item dim-group
                                                       (contains? active-dim-group-ids
                                                                  (:id dim-group))])]))

(defn timeline-panel
  "Панель для управления временем"
  []
  (let [d-group-mode @(subscribe [:grouping-mode])
        get-group-params (fn[gm]
                           (dom/menu-item-props gm
                                            (= d-group-mode gm)
                                            #(dispatch [:cashtime/set-grouping-mode gm])))]
    [:div
     [:a.ui.labeled.icon.tiny.button
      {:on-click #(dispatch [:cashtime/open-add-new-item])}
      [:i.add.icon]
      "добавить запись"]
     [:div.ui.text.right.floated.compact.menu
      [:div.header.item "Показать по "]
      [:a.item (get-group-params :by-day) "дням"]
      [:a.item (get-group-params :by-month) "месяцам"]
      [:a.item (get-group-params :by-year) "годам"]]]))


;; -- Модальное окно добавления/редактирования записи ----------------------------------

(def t1 (js-obj "pk" nil))


(defn new-modal-view
  "Модальное окно для добавления/редактирования записи"
  []
  (reagent/create-class
   {:component-did-mount (fn [this]
                           (.draggable (js/$ (reagent/dom-node this))
                                       #js {:containment "body"
                                            :handle ".ui.top.attached"
                                            :scroll false})
                           (set! (.-pk t1) (js/Pikaday.
                                                (clj->js {:field (.getElementById js/document "dateinput")
                                                          :format "DD.MM.YYYY"
                                                          :i18n mu/pikaday-i18n-params
                                                          :firstDay 1
                                                          ;; в дальнейшем добавить minDate для ограничения
                                                          :onSelect (fn [d]
                                                                      (dispatch [:cashtime/set-current-date d]))}))))
    :reagent-render
    (fn []
      (let [modal-params @(subscribe [:entry-modal-params])
            entry-params @(subscribe [:current-entry-params])
            show-modal? @(subscribe [:show-modal?])
            all-dim-groups (vals @(subscribe [:avail-dim-groups]))
            {:keys [dims date v-type v-flow v-summ]} entry-params]
        [:div.modal-for-item {:style {:visibility (if show-modal? "visible" "hidden")}}
         [:div.ui.raised.segments
           [:div.ui.top.attached.segment {:style {:cursor "move"}}
            [:div.ui.header.modal-header "Добавление записи"]
            [:i.large.remove.link.icon.close-modal-icon
              {:on-click #(dispatch [:cashtime/toggle-modal false])}]]
           [:div.ui.attached.segment
            [:div.ui.tiny.form
             [:div.two.fields
              [:div.field
                ; [:label "Поток"]
                [:div.ui.mini.compact.menu
                 [:a.item.inflow-btn
                   {:class (when (= v-flow :inflow) "active")
                    :on-click #(do
                                 (println "clicked inflow")
                                 (dispatch [:cashtime/toggle-current-v-flow :inflow]))}
                   "Поступление"]
                 [:a.item.outflow-btn
                   {:class (when (= v-flow :outflow) "active")
                    :on-click #(dispatch [:cashtime/toggle-current-v-flow :outflow])}
                   "Выплата"]]]
              [:div.field
              ;  [:label "Тип"]
               [:div.ui.mini.compact.menu
                [:a.item.fact-btn
                  {:class (when (= v-type :fact) "active")
                   :on-click #(do
                                (dispatch [:cashtime/toggle-current-v-type :fact]))}
                  "Факт"]
                [:a.item.plan-btn
                  {:class (when (= v-type :plan) "active")
                   :on-click #(dispatch [:cashtime/toggle-current-v-type :plan])}
                  "План"]]]]
             [:div.two.fields
              [:div.field
               [:label "Сумма"]
               [:input {:type "number"
                        :style {:text-align "right"}
                        :on-change #(dispatch [:cashtime/set-current-v-summ (dom/value-of-input %)])
                        :default-value v-summ}]]
              [:div.field
               [:label "Дата"]
               [:input.date-input {:type "text"
                                   :id "dateinput"
                                   :read-only "readonly"
                                   :default-value (when-let [v date]
                                                    (mu/to-print-date date))}]]]]

            [:div.ui.three.stackable.cards.dim-chooser-list
             (for [dim-group all-dim-groups]
               ^{:key (:id dim-group)} [:dim.card
                                        [rfr-u/dropdown-for-dim-group-comp
                                          dim-group
                                          (if-let [d (get dims (:id dim-group))] d "")
                                          "mini"
                                          #(dispatch [:cashtime/change-modal-dim-to
                                                       (:id dim-group) %])]])]]
           [:div.ui.bottom.attached.secondary.right.aligned.segment
            [:div.ui.deny.button
              {:on-click #(dispatch [:cashtime/toggle-modal false])}
              "Отменить"]
            [:div.ui.primary.button
              {:on-click #(dispatch [:cashtime/save-modal-item])}
              "Добавить"]]]]))}))



;; -- Главная вьюшка -------------------------------------
(defn main-view
  "Главная вьюшка"
  []
  [:div.ui.padded.grid
  ;  [:div.row
  ;   [:div.column
  ;    [:div.ui.button {:on-click #(dispatch [:cashtime/randomize-plain-entries])}
  ;     "Обновить данные"]
  ;    [:div.ui.button "Распечатать"]]]
   ;; панель управления временем c поиском
   [:div.row {:style {:padding-bottom "0"}}
    [:div.four.wide.column
     [:div.search-div
      [:div.ui.fluid.input
       [:input {:type "text"
                :placeholder "Введите часть названия для поиска"
                :auto-focus true
                :on-change #(dispatch [:cashtime/set-search-str (dom/value-of-input %)])}]]]]
    [:div.ten.wide.column
     [timeline-panel]]
    [:div.two.wide.column]]
   ;; переключатель измерений
   [:div.row {:style {:padding-bottom "0"}}
    [:div.four.wide.column
     [dimensions-toggler]]
    [:div.twelve.wide.column]]
   ;; сама таблица
   [:div.stretched.bottom.aligned.row
    [:div.four.wide.column
     [:div.dimensions-div
        ;; измерения
      [dimensions-panel]]]
    [:div.ten.wide.column
     [:div.values-div
        ;; записи с датами
      [entries-w-dates]]]

      ;; столбец итого по строкам
    [:div.two.wide.column
     [:div.row-totals-div
        ;; итого по строкам
      [row-totals]]]]
   ;; модальное окно для записи (добавления/редактирования)
   [new-modal-view]])
