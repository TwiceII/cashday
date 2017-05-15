(ns cashday.configurator.subs
    (:require [re-frame.core :as rfr]))


(rfr/reg-sub
  :current-in-edit-item
  (fn [db]
    (get-in db [:cfgr/work-entity-params :current-in-edit-item])))


(rfr/reg-sub
  :selected-item
  (fn [db]
    (get-in db [:cfgr/work-entity-params :selected-item])))
