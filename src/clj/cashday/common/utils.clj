(ns cashday.common.utils
  "Утилиты и вспомогательные функции"
  (:require [clojure.walk :as clj-walk]))

(defn nil-or-empty? [x]
  (or (nil? x) (empty? x)))


(defn in?
  "true if coll contains elm"
  [coll elm]
  (some #(= elm %) coll))
(defn replace-all-keys
  "Заменить все ключи в объекте на другие"
  [rm x]
  (clj-walk/postwalk
    (fn [el]
      (if (and (keyword? el)
               (contains? rm el))
        (get rm el)
        el))
    x))

(defn remove-keys
  "Удалить ключи из хм"
  [m ks]
  (select-keys m (->> (keys m)
                      (filter #(not (in? ks %))))))
(defn map-index-by
  "Сгруппировать в хм по какому-то полю
  замена для group-id, когда предполагается,
  что одному ключу одно значение"
  [k seq-of-maps]
  (reduce (fn [res-m iter-m]
            (if-let [v (get iter-m k)]
              (assoc res-m v iter-m)
              res-m))
          {} seq-of-maps))


(defn in?
  "true if coll contains elm"
  [coll elm]
  (some #(= elm %) coll))


(defn find-some
  "Найти в векторе/списке элемент,
  который удовлетв. условию"
  [pred-fn l]
  (some #(when (pred-fn %) %) l))


(defn coll-contains-subcoll?
  "Все ли элементы подсписка находятся в списке?"
  [coll subcoll]
  (println "coll-contains-subcoll")
  (println coll)
  (println subcoll)
  (println "----------")
  (every? #(in? coll %) subcoll))
