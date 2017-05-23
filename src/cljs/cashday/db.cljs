(ns cashday.db
  (:require [cashday.common.tuples :as tp]))


(def default-entry-params
  {:v-type :fact :v-flow :outflow :v-summ 0
   :dims {}
   :date (js/Date.)})


;; состояние программы
(def default-db
  {:name "cashday db"
   ;; какая страница открыта
   :active-window :cashtime ; [:cashtime :configurator]
   ;; данные для модал.окна подтверждения действия
   :approve-action-modal-params {:text "something"}
   ;; загружаемые в данный момент процессы
   :loading-processes #{}

   ;;;; ---- Данные для графика движения денег  ----
   :plain-entries [] ;; плоские данные с сервера

   ;; сами обработанные данные для таблицы
  ;  :inflow-entries nil
  ;  :outflow-entries nil

   ;; выбранная ячейка - опр-ся по дате, измерениям и типу потока
   :selected-cell-params {:date nil ;; iso-date
                          :flow-type nil ;; [:inflow :outflow]
                          :dims []
                          :ruled-dims []}

   ;; доступные группы измерений
   :avail-dim-groups nil

   ;; доступные таблицы соответствий
   :avail-rule-tables nil

   ;; активные (отображаемые) id групп измерений
   :active-dim-group-ids #{1 2}

   ;; значение поисковой строки
   :search-dim-str nil

   ;; сортировка для измерений
   :sort-dim-params {:group-id 1 ; по какой группе измерений сортируем
                     :desc? false}

   ;; параметры для дат
   :date-params {:grouping-mode :by-day} ; варианты: :by-day :by-month :by-year

   :current-entry-params default-entry-params

   ;; показывать ли модальное окно добавления записи
   :show-modal? false
   ;; параметры модального окна добавления записи
   :entry-modal-params {:mode :add-item
                        :title "Добавить запись"}

   ;;;; ---- Данные для конфигуратора ----

   ;; элемент, над к-ым выполняется какая-либо работа
   :cfgr/work-entity-params nil}) ; см. ниже пример



;; пример настроек для :cfgr/work-entity
{:entity-type :rule-table ;; тип активной сущности [:rule-table :dim-group]
 ;; режим работы над сущностью (могут содержаться кастомные)
 :work-mode :none ; [:none :add-item :edit-item ...]}
 ;; сущность редактируемая/добавляемая в данный момент
 :current-in-edit-entity nil

 ;;  параметры для редактирования подсущностей
 ;; (строки внутри таблицы, измерения в группе)
 :entity-self {} ;; сама сущность, над которой происходит работа
 ;; выбранный элемент сущности (н-р измерение внутри группы)
 :selected-item nil
 ;; элемент, поля которого изменяют (для добавления и редактирования)
 :current-in-edit-item nil}
