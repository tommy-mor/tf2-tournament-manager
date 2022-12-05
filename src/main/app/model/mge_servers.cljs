(ns app.model.mge-servers
  (:require
   [app.model.session :as session]
   [clojure.string :as str]
   [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button b textarea pre i]]
   [com.fulcrologic.fulcro.dom.html-entities :as ent]
   [com.fulcrologic.fulcro.dom.events :as evt]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
   [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
   [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.fulcro-css.css :as css]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   
   [com.fulcrologic.fulcro.dom.events :as evt]
   
   [com.fulcrologic.fulcro.data-fetch :as df]
   [taoensso.timbre :as log]))

(defsc Server [this {:keys [:server/id :server/last-pinged :server/remote-addr] :as props}]
  {:query [:server/id :server/last-pinged :server/remote-addr]
   :ident :server/id
   :initial-state {}}
  (div
   (div :.ui.container.segment
        {:onClick #(js/console.log "ars")}
        (pr-str props))))

(def ui-server (comp/factory Server {:keyfn :server/id}))

(defsc ServersPage [this {:keys [:servers/registered]}]
  {:query [{:servers/registered (comp/get-query Server)}]
   :ident (fn [] [:component/id :servers])
   :route-segment ["servers"]
   :initial-state {}
   :will-enter (fn [app route-params]
                 (log/info "entering route" route-params app)
                 (dr/route-deferred [:component/id :servers]
                                    (fn []
                                      (df/load! app
                                                [:component/id :servers]
                                                ServersPage
                                                {:post-mutation `dr/target-ready
                                                 :post-mutation-params
                                                 {:target [:component/id :servers]}}))))}
  (div
   (div :.ui.container.segment
        (h3 "registered servers")
        (doall (for [server registered]
                 (ui-server server))))))


