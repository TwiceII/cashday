;;;; =================================
;;;; Работа с рандомом
;;;; =================================
(ns cashday.common.random-utils
  (:require [cashday.common.moment-utils :as mu]))

(defn random-dim-id
  "Получить рандомный id измерения из группы"
  [all-dim-groups dim-group-id]
  (-> all-dim-groups
      (get dim-group-id)
      :dims
      keys
      rand-nth))

(defn rand-1-to
  "Рандомное число от 1 до Х (включительно)"
  [x]
  (-> x
      rand-int
      inc))

(defn rand-if
  "Вернуть значение value только если выпала вероятность указанная в success-percent"
  [success-percent value]
  (when (<= (rand-1-to 100) success-percent) value))

;; --- получение варианта по вероятности
(defn options->ranges
  [options]
  (->> options
       (reduce-kv (fn [total-m k p]
                    (-> total-m
                        (#(update % :ranges (fn [m] (assoc m [(inc (:last-v %)) (+ p (:last-v %))] k))))
                        (#(assoc % :last-v (+ p (:last-v %))))))
                  {:ranges nil :last-v 0})
       :ranges))

(defn opt-for-number
  [rngs number]
  (->> rngs
       vec
       (some #(when (and (<= (first (first %)) number)
                         (>= (second (first %)) number))
                (second %)))))

(defn rand-nth-by-percentage
  [options-w-percents]
  (let [rngs (options->ranges options-w-percents)
        r-number (rand-1-to 100)]
    (opt-for-number rngs r-number)))

;; получение варианта по вероятности ---

(defn rand-from-to
  "Рандомное число от Х до Y (включительно, можно использовать отриц.числа)"
  [from to]
  ;; REFACTOR: странный алгоритм
  (-> (+ (Math/abs from) (inc to))
      rand-int
      (+ (dec from))
      inc))

(defn random-group-ids
  "Получить рандомное кол-во рандомных id групп измерений"
  [all-dim-groups]
  (->> all-dim-groups
       keys
       (into [])
       shuffle
       (take (-> all-dim-groups
                 keys
                 count
                 rand-1-to))))


(defn random-tuple
  "Рандомный тапл измерений для записи"
  [dim-groups]
  (->> dim-groups
       random-group-ids
       (reduce (fn [m group-id]
                 (assoc m group-id (random-dim-id dim-groups group-id)))
               {})))

(defn random-iso-date
  "Рандомная дата в строчном iso формате"
  [from-d to-d]
  (-> (mu/random-date-between from-d to-d)
      (#(js/Date. (.getFullYear %) (.getMonth %) (.getDate %)))
      (.toISOString)))
