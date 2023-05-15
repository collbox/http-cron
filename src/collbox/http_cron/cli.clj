(ns collbox.http-cron.cli
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [collbox.http-cron.conversion :as conv]
   [collbox.http-cron.core :as core]
   [com.stuartsierra.component :as component]))

(defn- env-var [var]
  (when-let [v (System/getenv var)]
    (when-not (str/blank? v)
      v)))

(defn- ^:deprecated cli-options []
  [["-f" "--file FILE"
    "File name for cron.yaml-style job specification"
    :default (or (env-var "HTTP_CRON_JOB_FILE") "cron.yaml")]
   ["-h" "--help"
    "Display usage instructions"]
   ["-b" "--base-uri BASE_URI"
    "Base URL to POST to"
    :default (or (env-var "HTTP_CRON_BASE_URL")
                 (str "http://"
                      (or (env-var "HTTP_CRON_HOST") "localhost")
                      ":"
                      (or (env-var "HTTP_CRON_PORT") "8080")))]])

(defn- ^:deprecated error-msg [errors]
  (->> (concat
        ["Error parsing options:"
         ""]
        errors)
       (str/join \newline)))

(defn- ^:deprecated usage-msg [options-summary]
  (->> ["Usage: bin/run [options]"
        ""
        "Options:"
        options-summary]
       (str/join \newline)))

(defn- ^:deprecated validate-args [args]
  (let [{:keys [options errors summary]} (cli/parse-opts args (cli-options))]
    (cond
      (:help options)
      {:exit-message (usage-msg summary), :ok? true}
      errors
      {:exit-message (error-msg errors)}
      :else
      {:options options})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Public

(defn run [options]
  (let [file     (or (some-> (:file options) str)
                     (env-var "HTTP_CRON_JOB_FILE")
                     "cron.yaml")
        base-url (or (some-> (:base-url options) str)
                     (env-var "HTTP_CRON_BASE_URL")
                     (str "http://"
                          (or (:host options)
                              (env-var "HTTP_CRON_HOST")
                              "localhost")
                          ":"
                          (or (:port options)
                              (env-var "HTTP_CRON_PORT")
                              "8080")))]
    (if-not (.exists (io/file file))
      (do (.println *err* (format "File '%s' does not exist" file))
          (System/exit 1))
      (let [job-specs (conv/parse-cron-yaml file)
            _         (println (format
                                "Loaded %d job(s) from '%s', will POST to '%s'"
                                (count job-specs)
                                file
                                base-url))
            hc        (-> {:base-uri  base-url
                           :job-specs job-specs}
                          core/make-http-cron
                          component/start)]
        (. (Runtime/getRuntime)
           (addShutdownHook
            (Thread.
             #(do (println "Interrupt received, stopping system.")
                  (component/stop hc)
                  (shutdown-agents)))))
        @(promise)))))

(defn ^:deprecated -main [& args]
  (let [{:keys [options exit-message ok?]} (validate-args args)
        options                            (set/rename-keys options {:base-uri :base-url})]
    (doseq [ln ["************************************************************************"
                "This CLI interface is deprecated and may be removed in a future release."
                ""
                "Please see README.md for documentation on the new CLI."
                "************************************************************************"
                ""]]
      (.println *err* ln))
    (if exit-message
      (do (println exit-message)
          (System/exit (if ok? 0 1)))
      (run options))))
