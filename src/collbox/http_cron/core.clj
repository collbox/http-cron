(ns collbox.http-cron.core
  (:require
   [clj-http.client :as http]
   [clojure.tools.logging :as log]
   [clojurewerkz.quartzite.conversion :as qc]
   [clojurewerkz.quartzite.jobs :as qj]
   [clojurewerkz.quartzite.schedule.cron :as qcron]
   [clojurewerkz.quartzite.scheduler :as qs]
   [clojurewerkz.quartzite.triggers :as qt]
   [com.stuartsierra.component :as component]))

;; TODO: maybe a header (User-agent, etc.)
;; TODO: set `x-aws-sqsd-taskname` and `x-aws-sqsd-path` headers
;; TODO: probably want to uniquely handle `java.new.ConnectException`
;; so we're not spewing massive backtraces for this common occurrence.
(qj/defjob HTTPCronJob [ctx]
  (try
    (let [url (get (qc/from-job-data ctx) "url")]
      (log/infof "POST to '%s'" url)
      (http/post url)
      ;; TODO: consider logging HTTP status code, timing info?
      )
    (catch Exception ex
      (log/error ex))))

(defn- job-key [group-name name]
  (qj/key (str "jobs.http-cron." name) group-name))

(defn- trigger-key [group-name name]
  (qt/key (str "triggers.http-cron." name) group-name))

(defn- schedule-job [scheduler group-name base-uri {:keys [name schedule url]}]
  (let [url  (cond->> url
               (not (re-find #"^https?://" url))
               (str base-uri))
        job  (qj/build
              (qj/of-type HTTPCronJob)
              (qj/using-job-data {:url url})
              (qj/with-identity (job-key group-name name)))
        trig (qt/build
              (qt/with-identity (trigger-key group-name name))
              (qt/with-schedule (qcron/schedule (qcron/cron-schedule schedule))))]
    (qs/schedule scheduler job trig)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defrecord HTTPCron [;; params
                     base-uri
                     job-specs
                     ;; state
                     scheduler]
  component/Lifecycle
  (start [this]
    (if scheduler
      this
      (let [s          (qs/initialize)
            group-name (Integer/toHexString (System/identityHashCode this))]
        (doseq [js job-specs]
          (log/infof "Scheduling '%s' for [%s]" (:name js) (:schedule js))
          (schedule-job s group-name base-uri js))
        (qs/start s)
        (assoc this :scheduler s))))
  (stop [this]
    (when scheduler
      (qs/shutdown scheduler true))
    (assoc this :scheduler nil)))

(defn make-http-cron [{:keys [base-uri job-specs] :as config}]
  ;; TODO: validate data
  (map->HTTPCron config))
