(ns cashday.common.dom-utils
  (:require [reagent.core :as reagent]))

(defn value-of-input
  "Получить значение инпута"
  [el]
  (-> el .-target .-value))

(defn find-in-rdom-node
  "Найти элемент внутри реагент-дома
  this-rdom-node должно быть this"
  [this-rdom-node sel-str]
  (-> this-rdom-node
      reagent/dom-node
      js/$
      (.find sel-str)
      (aget 0)))

(defn $find-in-rdom-node
  [this-rdom-node sel-str]
  (js/$ (find-in-rdom-node this-rdom-node sel-str)))

(defn args-from-this
  "Получить аргументы для реагентовского :render"
  [t]
  (let [result  (-> t
                    .-props
                    (aget "argv")
                    rest
                    (into []))]
    result))


(def stop-prop-opts {:on-click #(.stopPropagation %)})


(defn no-propagation
  "Выполнить без propagation
   (чтобы не вызывались родительские события элемента)"
  [actions-fn]
  (fn [e]
    (do
      (actions-fn e)
      (.stopPropagation e))))


(defn menu-item-props
  "Получить типичные для пункта меню настройки
  (признак, что пункт активен и событие при щелчке на нем)"
  [item-key active? on-click-fn]
  {:class (when active? "active")
   :on-click #(do
                (on-click-fn item-key)
                (.stopPropagation %))})
