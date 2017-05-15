(ns cashday.effects
    (:require [re-frame.core :as rfr]))

;; вывод сообщения об успешной операции
(rfr/reg-fx
  :app/toastr-success
  (fn [msg]
    (js/console.log "toastr-success: " msg)
    (.success js/toastr msg)))


;; вывод сообщения об неуспешной операции
(rfr/reg-fx
  :app/toastr-error
  (fn [[msg header]]
    (js/console.log "toastr-error: " msg)
    (.error js/toastr header msg)))

;; вывод сообщения о предупреждении
(rfr/reg-fx
  :app/toastr-warning
  (fn [[msg header]]
    (js/console.log "toastr-warning: " msg)
    (.warning js/toastr header msg)))

;; открыть модальное окно по его id
(rfr/reg-fx
  :app/show-modal
  (fn [modal-id]
    (.modal (js/$ (str "#" modal-id)) "show")))
