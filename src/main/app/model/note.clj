(ns app.model.note
  (:require
   [app.model.mock-database :as db]
   [app.model.session :as session]
   [com.fulcrologic.guardrails.core :refer [>defn => | ?]]
   [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
   [taoensso.timbre :as log]
   [clojure.spec.alpha :as s]
   [xtdb.api :as xt]
   [clojure.set :refer [rename-keys]]))


(defresolver all-notes-resolver [{:keys [db] :as env} {:keys [session/account-id]  :as input}]
  {;;GIVEN nothing (e.g. this is usable as a root query)
   ;; I can output all accounts. NOTE: only ID is needed...other resolvers resolve the rest
   ::pc/input #{:session/account-id}
   ::pc/output [:notes/all]}
  (def env env)
  (def i input)
  (::session/current-session i)
  #_(xt/q (:db env) '{:find [(pull e [*])]
                    :where [[e :type :note]]})
  
  {:notes/all [{:note/id 3 :note/text "i am a note and i am epic"}
               {:note/id 4 :note/text "i am the second note"}
               {:note/id 5 :note/text "i am the third note"}]})

(defmutation edit-note [{:keys [db connection] :as env} {:keys [session/account-id] :as args}]
  {::pc/output []}
  (def a args)
  (let [now (java.util.Date.)
        user (-> env :ring/request :session :account/name)
        doc (cond-> args
              true
              (rename-keys {:note/id :xt/id})

              (= :new (:note/id args))
              (assoc :xt/id (random-uuid) :note/created now)

              true
              (assoc :type :note
                     :note/modified now
                     :owner (-> env :ring/request :session :account/name)))]
    (xt/submit-tx connection [[::xt/put doc]])
    {:tempids {:new (:xt/id doc)}}))

(def resolvers [all-notes-resolver edit-note])
