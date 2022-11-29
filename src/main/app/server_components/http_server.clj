(ns app.server-components.http-server
  (:require
    [app.server-components.config :refer [config]]
    [app.server-components.middleware :refer [middleware]]
    [app.server-components.nrepl-server :refer [nrepl-server]]
    
    [mount.core :refer [defstate]]
    [clojure.pprint :refer [pprint]]
    [org.httpkit.server :as http-kit]
    [taoensso.timbre :as log]))

(defstate http-server
  :start
  (let [cfg (::http-kit/config config)]
    (log/info "Starting HTTP Server with config " (with-out-str (pprint cfg)))
    (log/info "We have nrepl server?" (with-out-str (pprint nrepl-server)))
    (http-kit/run-server middleware cfg))
  :stop (http-server))


