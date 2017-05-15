;;;; =================================================================================
;;;; Работа с momentjs (и в общем с датами)
;;;; =================================================================================
(ns cashday.common.moment-utils
  (:require [clojure.string :as cs]
            [cljsjs.moment]))


(defn iso-str->moment [iso-str]
  (js/moment. iso-str))

(defn moment->iso-str
  [md]
  (str (.format md "YYYY-MM-DD") "T00:00:00Z"))

(defn to-print-datetime [iso-str]
  (.format
           ;;(iso-str->moment iso-str)
           (.utc js/moment iso-str)
    "DD.MM.YYYY HH:mm:ss"))


(defn to-print-date [iso-str]
  "Показать дату (строка в ISO) в формате ДД.ММ.ГГГГ"
  (when (not (cs/blank? iso-str))
    (.format (iso-str->moment iso-str) "DD.MM.YYYY")))


(defn ddmmyyyy->iso-date-str [ddmmyyyy-str]
  (.format (js/moment ddmmyyyy-str "DD.MM.YYYY" true) "YYYY.MM.DD"))


(defn ddmmyyyy->real-iso-date-str [ddmmyyyy-str]
  "Перевести в ISO8601 с учетом зоны (т.е не в UTC)"
  (when (not (cs/blank? ddmmyyyy-str))
    (str (.format (js/moment ddmmyyyy-str "DD.MM.YYYY" true) "YYYY-MM-DD")
         "T00:00:00Z")))


(defn current-date
  "Получить текущую дату"
  []
  (let [cdatetime (js/moment.)]
    (js/moment. (js/Date. (.year cdatetime)
                          (.month cdatetime)
                          (.date cdatetime)))))

(defn current-date-iso-str
  "Получить текущую дату в iso строке"
  []
  (-> (current-date)
      (.format "DD.MM.YYYY")
      (ddmmyyyy->real-iso-date-str)))


(defn add-days-moment
  "Получить дату с добавлением кол-ва дней"
  [moment-d days]
  (.add moment-d days "days"))


(defn diff-in-days
  "Получить разницу между двумя датами в днях"
  [moment1 moment2]
  ;; var a = moment([2007, 0, 29]);
  ;; var b = moment([2007, 0, 28]);
  ;; a.diff(b, 'days') // 1
  (.diff moment1 moment2 "days"))


; сравнения дат в формате momentjs
(defn moment-comparison
  [cond-fn-str moment-date1 moment-date2]
  ; убогий хак, т.к непонятно как вызвать переданную js ф-цию
  (case cond-fn-str
    "isSame" (.isSame moment-date1 moment-date2)
    "isBefore" (.isBefore moment-date1 moment-date2)
    "isAfter" (.isAfter moment-date1 moment-date2)))


(def moment-same? (partial moment-comparison "isSame"))
(def moment-before? (partial moment-comparison"isBefore"))
(def moment-after? (partial moment-comparison "isAfter"))


(defn str-moment-comparison
  [comp-fn dstr1 dstr2]
  (comp-fn (iso-str->moment dstr1)
           (iso-str->moment dstr2)))


(def str-moment-after? (partial str-moment-comparison moment-after?))
(def str-moment-before? (partial str-moment-comparison moment-before?))
(def str-moment-same? (partial str-moment-comparison moment-same?))


(defn sort-dates
  "Сортировать список дат (к-ые в ISO формате)"
  ([iso-dates] (sort-dates iso-dates false))
  ([iso-dates desc?]
   (->> iso-dates
        (into [])
        (sort (if desc? str-moment-after? str-moment-before?))
        (into []))))

;;    (let [dates-in-vector (into [] iso-dates)]
;;      (sort (if desc?
;;              str-moment-after?
;;              str-moment-before?) dates-in-vector))))


(defn random-date-between
  "Получить случайную vanilla js дату между двумя другими (со временем)"
  [from-d to-d]
  (->> (- (.getTime to-d) (.getTime from-d))
       (* (.random js/Math))
       (+ (.getTime from-d))
       (js/Date.)))


(defn print-date-in-needed-format
  [date d-group-mode]
  (when-not (clojure.string/blank? date)
    (-> date
        js/moment.
        (.format (case d-group-mode
                   :by-month "MMMM, YYYY"
                   :by-year "YYYY"
                   "DD.MM.YYYY")))))


(defn iso-date-by-month
  "Получить iso-str по месяцу (т.е 1-число месяца)"
  [iso-str]
  (-> (iso-str->moment iso-str)
      (#(js/moment. #js [(.year %) (.month %)]))
      moment->iso-str))

(defn iso-date-by-year
  "Получить iso-str по году (т.е 1 января этого года)"
  [iso-str]
  (-> (iso-str->moment iso-str)
      (#(js/moment. #js [(.year %)]))
      moment->iso-str))

(defn after-today?
  "Проверка, что js дата позже сегодняшней"
  [js-date]
  (-> js-date
      js/moment.
      (#(.isAfter % (js/moment.)))))


(def pikaday-i18n-params
  {:previousMonth "Предыдущий месяц"
   :nextMonth "Следующий месяц"
   :months ["Январь" "Февраль" "Март" "Апрель" "Май" "Июнь" "Июль" "Август" "Сентябрь" "Октябрь" "Ноябрь" "Декабрь"]
   :weekdays ["Воскресенье" "Понедельник" "Вторник" "Среда" "Четверг" "Пятница" "Суббота"]
   :weekdaysShort ["Вс" "Пн" "Вт" "Ср" "Чт" "Пт" "Сб"]})
