(ns cashday.cashtime.effects
  (:require [re-frame.core :refer [reg-fx]]))


; (reg-fx
;   :cashtime/item-modal-action
;   (fn [m-action]
;     (case m-action
;       :open (.modal (-> "#entry-modal" js/$) "show")
;       :hide (.modal (-> "#entry-modal" js/$) "hide"))))
