(ns app.model.account
  (:require
   [app.model.mock-database :as db]
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

(def resolvers [all-users-resolver account-resolver])
