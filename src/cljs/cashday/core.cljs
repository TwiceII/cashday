(ns cashday.core
  (:require [reagent.core :as reagent]
            [re-frame.core :as rfr]
            [day8.re-frame.http-fx]
            [cashday.events :as main.events]
            [cashday.effects]
            [cashday.subs]
            [cashday.views :as views]
            [cashday.config :as config]
            [cashday.cashtime.events :as casht.events]
            [cashday.cashtime.subs]
            [cashday.cashtime.effects]
            [cashday.configurator.events :as cfgr.events]
            [cashday.configurator.subs]
            [cashday.common.utils :as u]
            [cljsjs.moment]
            [cljsjs.moment.locale.ru]
            [cljsjs.toastr]
            [cljsjs.pikaday]
            [cljsjs.semantic-ui]
            [cljsjs.jquery-ui]))
            ; [cljsjs.react-draggable]))

(enable-console-print!)

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")))


(defn mount-root
  []
  (rfr/clear-subscription-cache!)
  (reagent/render [views/main-view]
                  (.getElementById js/document "app")))

(rfr/reg-event-fx
  :app/keypressed
  main.events/default-intercs
  (fn [{:keys [db]} [_ e]]
    (case (.-keyCode e)
      27 (case (:active-window db)
           :configurator (cfgr.events/keyboard-events db e)
           :cashtime (casht.events/keyboard-events db e)))))


(defn keydown [e]
  (when (u/in? [27 13] (.-keyCode e)) (.preventDefault e)
    (rfr/dispatch [:app/keypressed e])))


(defn ^:export init []
  (dev-setup)
  (.locale js/moment "ru")
  (rfr/dispatch-sync [:app/initialize-db])
  (set! (.-onkeydown js/document) keydown)
  ; (rfr/dispatch [:cashtime/randomize-plain-entries])
  ;; загрузить начальные данные
  (rfr/dispatch [:app/load-init-data])
  ; (rfr/dispatch [:app/post-transit])
  (mount-root))
