(ns app.server-components.nrepl-server
  (:require
    [app.server-components.config :refer [config]]
    [mount.core :refer [defstate]]
    [clojure.pprint :refer [pprint]]
    [taoensso.timbre :as log]
    [nrepl.server :refer [start-server stop-server]]))



(defstate nrepl-server
  :start
  (let [cfg (:nrepl-config config)]
    (log/info "Starting nrepl server with config" (with-out-str (pprint cfg)))
    (start-server :bind "0.0.0.0" :port (:port cfg)))
  
  :stop (stop-server nrepl-server))



