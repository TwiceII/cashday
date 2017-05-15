(ns cashday.subs
  (:require [re-frame.core :as rfr]
            [cashday.db :refer [default-db]]))

(defn reg-sub-db-plain
  "Регистрация плоской подписки на ключ
  (к-ый напрямую ссылается на значение в db))"
  [kw]
  (rfr/reg-sub kw (fn [db] (get db kw))))

;;;
;;; Сами подписки
;;;
;; -- Плоские подписки -------------------------------------------------------------------
(doseq [kw (keys default-db)]
  (reg-sub-db-plain kw))

;; -- Сложные подписки ---------------------------------------------------
;; получить группу по id группы из списка всех возможных
(rfr/reg-sub
  :dim-group-by-id
  (fn [_] (rfr/subscribe [:avail-dim-groups]))
  (fn [avail-dim-groups [_ dim-group-id]]
    (get avail-dim-groups dim-group-id)))

;; сортированный список измерений внутри группы
(rfr/reg-sub
  :sorted-dims-in
  :<- [:avail-dim-groups]
  (fn [avail-dim-groups [_ dim-group-id]]
    (->> (get avail-dim-groups dim-group-id)
         :dims
         vals
         (sort-by :name))))
