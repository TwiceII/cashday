(ns cashday.views
  (:require [re-frame.core :as rfr]
            [cashday.common.dom-utils :as dom]
            [cashday.common.views :as common.views]
            [cashday.cashtime.views :as cashtime.views]
            [cashday.configurator.views :as configurator.views]))


(defn approve-modal-view
  []
  (let [approve-action-modal-params @(rfr/subscribe [:approve-action-modal-params])]
    [common.views/approve-action-modal approve-action-modal-params]))


;; -- Главная вьюшка приложения
(defn main-view
  []
  (let [active-window         @(rfr/subscribe [:active-window])
        loading-processes     @(rfr/subscribe [:loading-processes])
        window-menu-item-prop (fn [k]
                                (dom/menu-item-props k (= active-window k)
                                                     #(rfr/dispatch [:app/switch-window %])))]
    [:div
      [:div.ui.basic.segment
        [:div.ui.massive.secondary.pointing.menu
          [:div.link.item (window-menu-item-prop :cashtime) "Движение денег"]
          [:div.link.item (window-menu-item-prop :configurator) "Конфигуратор"]
          (when-not (empty? loading-processes)
            [:div.item.loading-menu-item
              [:span.loading-text "загрузка данных..."]
              [:div.ui.inline.tiny.active.loader]])]
        ; [:h1.ui.header (case active-window
        ;                   :cashtime "Движение денег"
        ;                   :configurator "Конфигуратор измерений")
        [:div.ui.basic.segment
          [:div
            (case active-window
              :cashtime [cashtime.views/main-view]
              :configurator [configurator.views/main-view])]]]
      [approve-modal-view]]))
