(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'co.collbox/http-cron)
(def version "0.2.0")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     basis
                :src-dirs  ["src"]})
  (b/copy-dir {:src-dirs   ["src"]
               :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file  jar-file}))

(defn deploy [_]
  (dd/deploy {:installer :remote
              :artifact  jar-file
              :pom-file  (b/pom-path {:lib       lib
                                      :class-dir class-dir})}))