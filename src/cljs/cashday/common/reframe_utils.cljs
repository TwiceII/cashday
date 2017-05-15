(ns cashday.common.reframe-utils
  (:require [re-frame.core :as rfr]
            [ajax.core :as ajax]))


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
