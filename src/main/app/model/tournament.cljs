(ns app.model.tournament
  (:require
   [app.model.session :as session]
   [clojure.string :as str]
   [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button b textarea pre i a br]]
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

(defmutation start-tournament [{:keys [server/id]}]
  (remote [env] (m/returning env 'app.model.mge-servers/ServerPage)))

(defmutation delete-tournament [{:keys [tournament/id]}]
  (remote [env] (m/returning env 'app.model.mge-servers/ServerPage)))

(defsc Tournament [this {:keys [:tournament/id :server/id] :as props}]
  {:query [:tournament/id
           :server/id
           :attributes.name
           :attributes.tournamentType
           :attributes.fullChallongeUrl]
   :ident :tournament/id}
  
  
  "not sure how this will connect back to the server component, for now make it a button.
   once i have real data, then make sure it shows up."
  
  
  
  (def ps props)
  (div :.ui.container.segment
       (h3 (:attributes.name props))
       (p (:attributes.tournamentType props))
       (when (:active props) (b "I AM THE ACTIVE TOURNAMENT"))
       (a {:href (:attributes.fullChallongeUrl props)}
          "link")
       (br)
       (pr-str props)
       (button :.ui.button
               {:onClick #(comp/transact!
                           this
                           [(delete-tournament {:tournament/id (:tournament/id props)
                                                :server/id (:server/id props)})])}
               [(i :.icon.delete) "delete"])))

(def ui-tournament (comp/factory Tournament {:keyfn :tournament/id}))
