(ns cashday.common.reframe-utils
  (:require [re-frame.core :as rfr]
            [cashday.common.dom-utils :as dom]
            [ajax.core :as ajax]))

;; -- Стандартные вьюшки компонентов ------------------------------------------
(defn text-input-comp
  "Стандартный компонент для текстового инпута"
  [defvalue on-change-fn class placeholder]
  [:input {:type "text"
           :autoFocus true
           :placeholder placeholder
           :on-change #(on-change-fn %)
           :default-value defvalue}])

(defn text-input-w-buttons-comp
  [defvalue on-change-fn on-approve-fn on-cancel-fn class placeholder]
  [:div.ui.focus.fluid.small.input.text-input-w-buttons dom/stop-prop-opts
    [text-input-comp defvalue on-change-fn class placeholder]
    [:div.icon-buttons-div
      [:i.remove.small.right.floated.bordered.icon
        {:title "Отмена"
         :on-click (dom/no-propagation on-cancel-fn)}]
      [:i.checkmark.green.small.right.floated.bordered.inverted.icon
        {:title "Сохранить"
         :on-click (dom/no-propagation on-approve-fn)}]]])


;; -- reframe http.xhrio ------------------------------------------------------

(defn http-xhrio-method
  [method uri data success-event-k success-title failure-title]
  {:method          method
   :uri             uri
   :params          data
   :format          (ajax/transit-request-format)
   :response-format (ajax/transit-response-format)
   :on-success      [:default-process-xhrio success-event-k
                                            success-title
                                            failure-title]
   :on-failure      [:failure-resp]})

(def http-xhrio-post    (partial http-xhrio-method :post))
(def http-xhrio-delete  (partial http-xhrio-method :delete))


(defn xhrio-result-fxs
  [value success-event-k success-title failure-title]
  (if (= :failure (:result value))
    (let [errors-str (str "<ul>"
                          (reduce (fn [e-str error]
                                    (str e-str "<li>" error "</li>"))
                                  "" (:errors value))
                          "</ul>")]
      {:app/toastr-warning [failure-title errors-str]})
    {:app/toastr-success success-title
     :dispatch [success-event-k value]}))
