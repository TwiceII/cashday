;;;; =========================================================
;;;; Общие вьюшки для всего приложения
;;;; =========================================================
(ns cashday.common.views
  (:require [re-frame.core :as rfr]
            [reagent.core :as reagent]
            [cashday.common.dom-utils :as dom]))


(defn buttons-save-or-cancel-view
  "Две кнопки: сохранить и отмена"
  [on-save-fn on-cancel-fn]
  [:div
    [:div.ui.tiny.icon.positive.button
      {:title "Сохранить"
       :on-click on-save-fn}
      [:i.checkmark.icon]]
    [:div.ui.basic.tiny.icon.button
      {:title "Отмена"
       :on-click on-cancel-fn}
      [:i.remove.icon]]])


(defn buttons-edit-or-delete-view
  "Две кнопки: редактировать и удалить"
  [on-edit-fn on-delete-fn]
  [:div
    [:div.ui.tiny.icon.positive.button
      {:title "Редактировать"
       :on-click on-edit-fn}
      [:i.write.icon]]
    [:div.ui.tiny.icon.negative.button
      {:title "Удалить"
       :on-click on-delete-fn}
      [:i.trash.icon]]])


(defn approve-action-modal
  "Модальное окно подтверждения какого-либо действия (например удаления)"
  [{:keys [header text approve-text cancel-text approve-fn]}]
  (reagent/create-class
    {:component-did-mount (fn [rcomp]
                            (.modal (js/$ "#global-approve-modal")
                                    #js {:detachable false
                                         :closable true
                                         :onApprove (-> rcomp
                                                        reagent/props
                                                        :approve-fn)}))
     :component-did-update (fn [rcomp]
                             (.modal (js/$ "#global-approve-modal")
                                     #js {:detachable false
                                          :closable true
                                          :onApprove (-> rcomp
                                                         reagent/props
                                                         :approve-fn)}))
     :display-name "approve-action-modal"
     :reagent-render
       (fn [{:keys [header text approve-fn approve-text cancel-text]}]
        [:div.ui.small.modal {:id "global-approve-modal"}
          [:i.close.icon]
          (when (some? header)
            [:div.header header])
          [:div.content
            [:p text]]
          [:div.actions
            [:div.ui.approve.positive.button (or approve-text "Да")]
            [:div.ui.deny.button (or cancel-text "Нет")]]])}))
