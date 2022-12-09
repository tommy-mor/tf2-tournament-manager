(ns app.model.mge-servers
  (:require
   [app.model.session :as session]
   [app.model.tournament :as tournament]
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

(defsc Player [this {:keys [:player/name
                            :player/steamid]}]
  {:query [:player/name :player/steamid]
   :ident :player/steamid}
  (div :.ui.container.segment
       name))

(def ui-player (comp/factory Player {:keyfn :player/steamid}))

(defsc ServerPage [this {:keys [:server/id
                                :server/last-pinged
                                :server/game-addr
                                :server/api-addr
                                :server/players
                                :server/active-tournament
                                :server/tournaments] :as props}]
  {:query [:server/id
           :server/last-pinged
           :server/game-addr
           :server/api-addr
           {:server/players (comp/get-query Player)}
           {:server/tournaments (comp/get-query tournament/Tournament)}
           :server/active-tournament]
   :ident :server/id
   :route-segment ["server" :server/id]
   :initial-state {}
   :will-enter
   (fn [app route-params]
     (log/info "entering route" route-params app)
     (let [ident [:server/id (uuid (:server/id route-params))]]
       (log/info ident)
       (dr/route-deferred ident
                          (fn []
                            (df/load! app
                                      ident
                                      ServerPage
                                      {:post-mutation `dr/target-ready
                                       :post-mutation-params
                                       {:target ident}})))))}
  (div
   (h3 "full server page")
   (button :.ui.secondary.button
           {:onClick #(df/load! this
                                [:server/id id]
                                ServerPage)}
           [(i :.refresh.icon) "refresh"])
   (div :.ui.container.segment
        {:onClick #(js/console.log "ars")}
        (pr-str props))
   (div :.ui.container.segment
        (doall (for [player players]
                 (ui-player player))))
   (when (not (:tournament/id active-tournament))
     (button :.ui.button
             {:onClick (fn start []
                         (comp/transact! this
                                         [(tournament/start-tournament {:server/id id})]))}
             [(i :.icon.play) "start tournament"]))
   (doall
    (for [tournament tournaments]
      (if (= (:tournament/id active-tournament)
             (:tournament/id tournament))
        (tournament/ui-tournament (assoc tournament :active true))
        (tournament/ui-tournament tournament))))))

(def ui-server-page (comp/factory ServerPage))

(defsc Server [this {:keys [:server/id :server/last-pinged :server/game-addr ] :as props}]
  {:query [:server/id :server/last-pinged :server/game-addr]
   :ident :server/id
   :initial-state {}}
  (div
   (div :.ui.container.segment
        {:onClick #(dr/change-route this ["server" id])}
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


