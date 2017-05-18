(ns cashday.events
  (:require [re-frame.core :as rfr]
            [ajax.core :as ajax]
            [cashday.db :as db]
            [cashday.common.utils :as u]))

;; базовый список интерсептеров
(def default-intercs
  [(when ^boolean js/goog.DEBUG rfr/debug)])


(defn add-loading-process
  "Добавить ключ в список загружаемых процессов"
  [db load-process-key]
  (update db :loading-processes conj load-process-key))


(defn remove-loading-process
  "Убрать ключ из списка загр. процессов"
  [db load-process-key]
  (update db :loading-processes disj load-process-key))

;; инициализация состояния программы
(rfr/reg-event-db
 :app/initialize-db
 (fn [_ _]
   db/default-db))

(defn reg-simple-set-event
  "Рег-ция обычного обработчика события,
  к-ый устанавливает новое зн-ие по пути"
  [event-kw db-path]
  (rfr/reg-event-db
    event-kw
    [(when ^boolean js/goog.DEBUG rfr/debug)
     (rfr/path db-path)
     rfr/trim-v]
    (fn [old-v [new-v]]
      new-v)))

;; смена страницы
(reg-simple-set-event :app/switch-window [:active-window])

;; открытие модального окна подтверждения действия
(rfr/reg-event-fx
  :app/show-approve-modal
  default-intercs
  (fn [{:keys [db]} [_ modal-params]]
    (-> {:db (assoc db :approve-action-modal-params modal-params)
         :app/show-modal "global-approve-modal"})))


;; -- Ajax transit ------------------------------------------------------------

;; при успешном ajax, меняем значение в db
(rfr/reg-event-db
  :success-resp-set-db
  default-intercs
  (fn [db [_ load-key path value]]
    (-> db
        (remove-loading-process load-key)
        (assoc-in path value))))

;; при успешном обновлении групп измерений
(rfr/reg-event-db
  :success-resp-set-dim-groups
  default-intercs
  (fn [db [_ value]]
    (-> db
        (remove-loading-process :l-avail-dim-groups)
        (assoc :avail-dim-groups value)
        (assoc :active-dim-group-ids (into #{} (keys value))))))

;; при неуспешном ajax, выводим ошибку
(rfr/reg-event-fx
  :failure-resp
  default-intercs
  (fn [{:keys [db]} [_ error-m]]
    {:app/toastr-error [(:debug-message error-m)
                        (:last-error  error-m)]}))

;; стандартный обработчик ajax, проверяет результат работы
(rfr/reg-event-fx
  :default-process-xhrio
  default-intercs
  (fn [_ [_ success-event-k success-title failure-title value]]
    (js/console.log "======================================")
    (js/console.log "process xhrio value: ")
    (js/console.log value)
    (js/console.log "======================================")
    (if (= :failure (:result value))
      (let [errors-str (str "<ul>"
                            (reduce (fn [e-str error]
                                      (str e-str "<li>" error "</li>"))
                                    "" (:errors value))
                            "</ul>")]
        {:app/toastr-warning [failure-title errors-str]})
      {:app/toastr-success success-title
       :dispatch [success-event-k value]})))



(defn http-xhrio-set-db
  [uri load-key path]
  {:method          :get
   :uri             uri
   :format          (ajax/transit-request-format)
   :response-format (ajax/transit-response-format)
   :on-success      [:success-resp-set-db load-key path]
   :on-failure      [:failure-resp]})

(rfr/reg-event-fx
  :success-resp-print
  default-intercs
  (fn [_ [_ msg value]]
    (println value)
    {:app/toastr-success msg}))

(defn http-xhrio-post
  [uri data msg]
  {:method          :post
   :uri             uri
   :params          data
   :format          (ajax/transit-request-format)
   :response-format (ajax/transit-response-format)
   :on-success      [:success-resp-print msg]
   :on-failure      [:failure-resp]})


;; загрузить с сервера группы измерений
(rfr/reg-event-fx
  :app/load-avail-dim-groups
  default-intercs
  (fn [{:keys [db]} [_]]
    {:db (add-loading-process db :l-avail-dim-groups)
     :http-xhrio  {:method          :get
                   :uri             "/dimgroups"
                   :format          (ajax/transit-request-format)
                   :response-format (ajax/transit-response-format)
                   :on-success      [:success-resp-set-dim-groups]
                   :on-failure      [:failure-resp]}}))


;; загрузить с сервера таблицы соответствий
(rfr/reg-event-fx
  :app/load-avail-rule-tables
  default-intercs
  (fn [{:keys [db]} [_]]
    {:db (add-loading-process db :l-avail-rule-tables)
     :http-xhrio (http-xhrio-set-db "/ruletables"
                                    :l-avail-rule-tables
                                    [:avail-rule-tables])}))


;; загрузить с сервера плоские данные
(rfr/reg-event-fx
  :app/load-plain-entries
  default-intercs
  (fn [{:keys [db]} [_]]
    {:db (add-loading-process db :l-plain-entries)
     :http-xhrio (http-xhrio-set-db "/plainentries"
                                    :l-plain-entries
                                    [:plain-entries])}))


;; загрузить с сервера изначальные данные
(rfr/reg-event-fx
  :app/load-init-data
  default-intercs
  (fn [{:keys [db]} [_]]
    {:dispatch-n [[:app/load-avail-dim-groups]
                  [:app/load-avail-rule-tables]
                  [:app/load-plain-entries]]}))

(rfr/reg-event-fx
  :app/post-transit
  default-intercs
  (fn [_ _]
    {:http-xhrio (http-xhrio-post "/transitpost"
                                  {:some-data {:id #uuid "590200ac-1ad2-4bcc-b099-3f3f60c48383"
                                               :name {:collection [3 4 5]}}}
                                  "Запостили-то удачно-с!")}))
