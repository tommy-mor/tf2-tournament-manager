(ns app.model.account
  (:require
   [app.model.mock-database :as db]
   [app.model.session :as session]
   [com.fulcrologic.guardrails.core :refer [>defn => | ?]]
   [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
   [taoensso.timbre :as log]
   [clojure.spec.alpha :as s]
   [xtdb.api :as xt]))

(defn all-account-ids
  "Returns a sequence of UUIDs for all of the active accounts in the system"
  [db]
  (map (comp :xt/id first) (xt/q db '{:find [(pull e [:xt/id])]
                                      :where [[e :type :account]
                                              ]})))

(defresolver all-users-resolver [{:keys [db]} input]
  {;;GIVEN nothing (e.g. this is usable as a root query)
   ;; I can output all accounts. NOTE: only ID is needed...other resolvers resolve the rest
   ::pc/output [{:all-accounts [:account/id]}]}
  {:all-accounts (mapv
                  (fn [id] {:account/id id})
                  (all-account-ids db))})

(>defn get-account [db id subquery]
       [any? uuid? vector? => (? map?)]
       (xt/pull db subquery id))

(defresolver account-resolver [{:keys [db] :as env} {:account/keys [id]}]
  {::pc/input  #{:account/id}
   ::pc/output [:account/email]}
  (get-account db id [:account/email]))

(defresolver logged-in-user-id [{:keys [db]} {::session/keys [current-session]}]
  {::pc/output [:session/account-id]}
  (if (:session/valid? current-session)
    {:session/account-id
     (ffirst (xt/q (xt/db db/conn) '{:find [id]
                                     :where [[e :type :account]
                                             [e :account/email email]
                                             [e :xt/id id]]
                                     :in [email]}
                   (:account/name current-session)))}))

(defresolver all-notes-resolver [{:keys [db] :as env} {:keys [session/account-id]  :as input}]
  {;;GIVEN nothing (e.g. this is usable as a root query)
   ;; I can output all accounts. NOTE: only ID is needed...other resolvers resolve the rest
   ::pc/input #{:session/account-id}
   ::pc/output [{:notes [:notes/all]}]}
  (def env env)
  (def i input)
  (::session/current-session i)
  (-> env :ring/request :session :account/name)
  {:notes {:notes/all [{:note/id 3 :note/text "i am a note and i am epic"}
                       {:note/id 4 :note/text "i am the second note"}
                       {:note/id 5 :note/text "i am the third note"}]}})

(defmutation submit-note [{:keys [db] :as env} {:keys [notebody]}]
  {::pc/output []}
  {:notebody notebody})

(def resolvers [all-users-resolver account-resolver all-notes-resolver logged-in-user-id])
