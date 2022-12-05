(ns app.server-components.config
  (:require
    [mount.core :refer [defstate args]]
    [com.fulcrologic.fulcro.server.config :refer [load-config!]]
    [taoensso.timbre :as log]
    [taoensso.timbre.appenders.core :as appenders]))


(defn configure-logging! [config]
  (let [{:keys [taoensso.timbre/logging-config]} config]
    (log/info "Configuring Timbre with " logging-config)
    (log/merge-config!
     {:min-level :info
      :appenders {:spit (appenders/spit-appender {:fname (:path logging-config)})}})))


(defstate config
  :start (let [{:keys [config] :or {config "config/dev.edn"}} (args)
               configuration (load-config! {:config-path config})]
           (log/info "Loaded config" config)
           (configure-logging! configuration)
           configuration))

