(ns cashday.configurator.subs
    (:require [re-frame.core :as rfr]))

;; -- Ф-ции для подписок ------------------------------------------------------
(defn entity-worked-on?
  [work-entity-params entity-type entity]
  (and (= (:entity-type work-entity-params) entity-type)
       (= (:entity-self work-entity-params) entity)))

;; -- Сами подписки -----------------------------------------------------------
(rfr/reg-sub
  :current-in-edit-item
  (fn [db]
    (get-in db [:cfgr/work-entity-params :current-in-edit-item])))


(rfr/reg-sub
  :selected-item
  (fn [db]
    (get-in db [:cfgr/work-entity-params :selected-item])))

;; происходит ли работа над текущей сущностью (группой измерения или таблицей)
(rfr/reg-sub
  :cfgr/entity-worked-on?
  :<- [:cfgr/work-entity-params]
  (fn [work-entity-params [_ entity-type entity]]
    (entity-worked-on? work-entity-params entity-type entity)))
