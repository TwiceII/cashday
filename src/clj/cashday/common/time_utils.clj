(ns cashday.common.time-utils
  "Функции для работы с датами"
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clj-time.local :as tl]
            [clj-time.format :as tf]))

; форматтер для строк вида "2016-07-14T00:00:00Z" (для приходящих js дат)
(def iso-formatter (tf/formatters :date-time-no-ms))
; форматтер для строк вида "2016-07-14T00:00:000Z" (для приходящих js дат)
(def iso-w-ms-formatter (tf/formatters :date-time))

; форматтер для дат вида "14.07.2016"
(def default-formatter (tf/formatter "dd.MM.yyyy"))
; форматтер для дат вида "2016-07-14"
(def ymd-formatter (tf/formatter "yyyy-MM-dd"))

(defn datetime->iso-js
  [datetime]
  (tf/unparse iso-formatter datetime))

(defn datetime-in-current
  "Получить датувремя с нужным часовым поясом (текущая датавремя)"
  [datetime]
  (t/to-time-zone datetime (t/time-zone-for-offset +6)))


(defn now-datetime
  "Получить текущую датувремя в нужном текущем поясе и т.д."
  []
  (datetime-in-current (t/now)))


(def time-formatter (tf/formatter "HH:mm:ss.SSS"))

(defn time->str
  "Получить строку времени в нашем часовом поясе"
  [dt]
  (tf/unparse (tf/with-zone time-formatter
                            (t/time-zone-for-offset +6))
              dt))


(defn datetime->str
  "Получить строку даты и времени в нашем часовом поясе в виде
  14.07.2016 13:04:45"
  [dt]
  (tf/unparse (tf/with-zone (tf/formatter "dd.MM.yyyy HH:mm:ss")
                            (t/time-zone-for-offset +6))
              dt))

(defn datetime-raw->str
  "Получить строку даты и времени в указанном часовом поясе в виде
  14.07.2016 13:04:45"
  [dt]
  (tf/unparse (tf/formatter "dd.MM.yyyy HH:mm:ss") dt))


(defn date->ddmmyyyy
  "Получить дату в строке ДД.ММ.ГГГГ"
  [dt]
  (tf/unparse default-formatter dt))


(defn date->ddmmyyyy-w-timezone
  "Получить дату в строке ДД.ММ.ГГГГ (для текущей зоны)"
  [dt]
  (tf/unparse (tf/with-zone default-formatter
                            (t/time-zone-for-offset +6))
              dt))


(defn current-date-str
  "Получить текущую дату в строке"
  []
  (tf/unparse (tf/formatter "yyyy-MM-dd") (now-datetime)))


(defn current-time-str
  "Получить текущее время в строке"
  []
  (time->str (t/now)))



(defn parse [date-str]
  "Парсит в нескольких форматах: js-iso, dd.MM.yyyy, yyyy-mm-dd"
  (try
    (tf/parse default-formatter date-str)
    (catch Exception e
      (try
        (tf/parse ymd-formatter date-str)
        (catch Exception e
          (try
            (tf/parse iso-formatter date-str)
            (catch Exception e
              (try
                (tf/parse iso-w-ms-formatter date-str)
                (catch Exception e
                  (tf/parse (tf/with-zone (tf/formatter "YYYY-MM-dd'T'HH:mm:ss")
                                          (t/time-zone-for-offset +6)) date-str))))))))))




; (->> "2016-12-30T10:09:15"
;       parse
; ;;       (tf/parse (tf/with-zone (tf/formatter "YYYY-MM-dd'T'HH:mm:ss")
; ;;                               (t/time-zone-for-offset +6)))
;      datetime-in-current)



(defn joda-date?
  "Проверка, что значение является org.joda.time.DateTime"
  [value]
  (instance? org.joda.time.DateTime value))


(defn joda-date->java-date
  "Конвертировать joda-date в java.util.Date"
  [joda-date]
  (tc/to-date (tc/to-local-date-time joda-date)))


; (defn in-hm-joda-dates->java-dates
;   "Преобразовать все записи в хм из joda date в java.utils.date"
;   [obj]
;   (let [convert-fn (fn [[k v]]
;                      (if (joda-date? v)
;                        [k (joda-date->java-date v)]
;                        [k v]))]
;     (clojure.walk/postwalk (fn [x]
;                              (if (map? x)
;                                (into {} (map convert-fn x))
;                                x))
;                            obj)))


(defn date-considering-weekends
  "Получить дату с учетом выходных
  (если выпадает на выходные - переносится на ПН след.недели"
  [d]
  (case (t/day-of-week d)
    6 (t/plus d (t/days 2))
    7 (t/plus d (t/days 1))
    d))


(defn dss-consider-weekends-and-date-to-check
  "Получает [{дата, сумма}] с учетом выходных
	и с учетом текущей даты"
  [init-dss date-to-check]
  (map #(if (not (t/before? (:date %) date-to-check))
          (assoc % :date (date-considering-weekends (:date %)))
          %) init-dss))

(defn base-sort-by-date
  [date-key desc? dates]
  (let [dates-in-vector (into [] dates)]
    (sort-by date-key (if desc? t/after? t/before?) dates-in-vector)))

(def base-sort-by-date-key
  "Сортировать список по полю :date "
  (partial base-sort-by-date :date))

;; (defn base-sort-by-date-key
;;   [desc? dates]
;;   (let [dates-in-vector (into [] dates)]
;;     (sort-by :date (if desc? t/after? t/before?) dates-in-vector)))


; от старых к новым
(def sort-by-date-key (partial base-sort-by-date-key false))
; от новых к старым
(def sort-by-date-key-desc (partial base-sort-by-date-key true))

;23.07.2016, 01:00

(defn parse-datetime-sync
  [dt-str]
  (datetime-in-current (tf/parse (tf/formatter "dd.MM.yyyy, HH:mm") dt-str)))

(defn parse-date-sync
  [dt-str]
  (datetime-in-current (tf/parse (tf/formatter "dd.MM.yyyy") dt-str)))

;; (parse-datetime-sync "23.07.2016, 01:00")
;; (parse-date-sync "23.07.2016")

(defn only-date
  "Конвертировть в java дату (берет только дату из строки)"
  [dt]
  (tl/to-local-date-time
      (t/date-time (t/year dt) (t/month dt) (t/day dt))))

;; -- Работа с java.date

(defn jdate
  "Получить java date объект"
  [year month day]
  (-> (t/date-time year month day)
      (tc/to-date)))

(defn jdate->iso-str
  "Получить строку ISO из java date объекта"
  [jd]
  (datetime->iso-js (tc/from-date jd)))

(defn parse-to-jdate
  "Спарсить и перевести в java.date"
  [s]
  (-> s parse tc/to-date))


(defn iso-w-ms->jdate
  "Получить из ISO с миллисекундами Joda-date"
  [iso-date]
  (->> iso-date
       (tf/parse (tf/with-zone iso-w-ms-formatter (t/time-zone-for-offset +6)))
       ;; берем только дату без времени
       (#(t/date-time (t/year %) (t/month %) (t/day %)))
       (tc/to-date)))


(defn compare-jdates
  [jdate1 jdate2 d-group-mode]
  (case d-group-mode
    :by-month   (and (= (.getYear jdate1) (.getYear jdate2))
                     (= (.getMonth jdate1) (.getMonth jdate2)))
    :by-year    (= (.getYear jdate1) (.getYear jdate2))))
