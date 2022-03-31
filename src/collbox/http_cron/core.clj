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

(qj/defjob HTTPCronJob [ctx]
  (try
    (let [{:strs [name path url]} (qc/from-job-data ctx)]
      (log/infof "POSTing to '%s'..." url)
      (let [{:keys [request-time status]}
            (http/post url {:headers            {"User-Agent"          "http-cron/0.1"
                                                 "x-aws-sqsd-path"     path
                                                 "x-aws-sqsd-taskname" name}
                            :socket-timeout     10000
                            :connection-timeout 10000
                            :throw-exceptions   false})]
        (log/infof "HTTP %d (%dms)" status request-time)))
    (catch Exception ex
      (log/error (.getMessage ex)))))

(defn- job-key [group-name name]
  (qj/key (str "jobs.http-cron." name) group-name))

(defn- trigger-key [group-name name]
  (qt/key (str "triggers.http-cron." name) group-name))

(defn- schedule-job [scheduler group-name base-uri {:keys [name schedule url]}]
  (let [full-url (cond->> url
                   (not (re-find #"^https?://" url))
                   (str base-uri))
        job      (qj/build
                  (qj/of-type HTTPCronJob)
                  (qj/using-job-data {:name name
                                      :path url
                                      :url  full-url})
                  (qj/with-identity (job-key group-name name)))
        trig     (qt/build
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
      (qs/shutdown scheduler))
    (assoc this :scheduler nil)))

(defn make-http-cron [{:keys [base-uri job-specs] :as config}]
  ;; TODO: validate data
  (map->HTTPCron config))
