(ns app.model.session
  (:require
   [app.model.mock-database :as db]
   [com.fulcrologic.guardrails.core :refer [>defn => | ?]]
   [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver defmutation]]
   [taoensso.timbre :as log]
   [clojure.spec.alpha :as s]
   [com.fulcrologic.fulcro.server.api-middleware :as fmw]
   [xtdb.api :as xt]))

(defonce account-database (atom {}))

(defresolver current-session-resolver [env input]
  {::pco/output [{::current-session [:session/valid? :account/name]}]}
  (let [{:keys [account/name session/valid?]} (get-in env [:ring/request :session])]
    (if valid?
      (do
        (log/info name "already logged in!")
        {::current-session {:session/valid? true :account/name name}})
      {::current-session {:session/valid? false}})))

(defn response-updating-session
  "Uses `mutation-response` as the actual return value for a mutation, but also stores the data into the (cookie-based) session."
  [mutation-env mutation-response]
  (let [existing-session (some-> mutation-env :ring/request :session)]
    (fmw/augment-response
      mutation-response
      (fn [resp]
        (let [new-session (merge existing-session mutation-response)]
          (assoc resp :session new-session))))))

(defn get-account [db email]
  (ffirst (xt/q db '{:find [(pull e [*])]
                                  :where [[e :type :account]
                                          [e :account/email email]]
                                  :in [email]}
                email)))

(defmutation login [{:keys [db] :as env} {:keys [username password]}]
  {::pco/output [:session/valid? :account/name]}
  (log/info "Authenticating" username)
  (let [{expected-email    :account/email
         expected-password :account/password} (get-account db username)]
    (if (and (= username expected-email) (= password expected-password))
      (response-updating-session env
                                 {:session/valid? true
                                  :account/name   username})
      (do
        (log/error "Invalid credentials supplied for" username)
        (throw (ex-info "Invalid credentials" {:username username}))))))

(defmutation logout [env params]
  {::pco/output [:session/valid?]}
  (response-updating-session env {:session/valid? false :account/name ""}))

(defn create-user! [{:keys [email password]}]
  (xt/submit-tx db/conn [[::xt/put {:xt/id (random-uuid)
                                    :type :account
                                    :account/email email
                                    :account/password password}]]))

(defmutation signup! [env {:keys [email password] :as input}]
  {::pco/output [:signup/result]}
  (create-user! {:email email :password password})
  {:signup/result "OK"})


(def resolvers [current-session-resolver login logout signup!])
