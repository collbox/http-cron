(ns collbox.http-cron.conversion
  (:require [clj-yaml.core :as yaml]
            [clojure.string :as str]
            [clojure.java.io :as io]))

;; Quartz's cron has some quirks
;; (http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html).
;;
;; - It expects a leading 'seconds' field.
;; - Both day-of-month and day-of-week cannot be "*"--one must be a
;;   "?".  Ridiculous.
;; - Specifying a day-of-week and "*" for day-of-month throws error
;;   that both cannot be provided.
(defn- translate-cron-expression [ce]
  (let [v (str/split ce #" ")]
    (if (= 5 (count v))
      (->> (cond-> v
             (= "*" (nth v 2))
             (assoc 2 "?")
             (= "0" (nth v 4))
             (assoc 4 "7"))
           (concat [0])
           (str/join " "))
      ce)))

;; Naming could be tough here.  This is an AWS-style cron.yaml, not
;; the same as what Google App Engine uses.  I don't know if anyone
;; else uses the same format or if it should be considered an Amazon
;; thing.
;;
;; It's quite similar, but Google allows more flexible scheduling
;; syntax and other things like retry-parameters and timezones.
(defn parse-cron-yaml [file]
  (->> file
       io/reader
       yaml/parse-stream
       :cron
       (map #(update % :schedule translate-cron-expression))))
