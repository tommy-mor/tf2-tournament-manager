(ns app.model.tournament
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

(defmutation start-tournament [{:keys [server/id]}]
  (remote [env] (m/returning env 'app.model.mge-servers/ServerPage)))

(defsc Tournament [this {:keys [:tournament/id :tournament/name :tournament/server-id] :as props}]
  {:query [:tournament/id :tournament/name :tournament/server-id]
   :ident :tournament/id}
  
  
  "not sure how this will connect back to the server component, for now make it a button.
   once i have real data, then make sure it shows up."
  
  
  
  (div :.ui.container.segment
       (h3 "I am a tournament")
       (pr-str props)))

(def ui-tournament (comp/factory Tournament {:keyfn :player/steamid}))
