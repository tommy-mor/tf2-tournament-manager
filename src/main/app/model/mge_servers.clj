(ns app.model.mge-servers
  (:require
   [app.model.mock-database :as db]
   [app.model.session :as session]
   [com.fulcrologic.guardrails.core :refer [>defn => | ?]]
   [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
   [taoensso.timbre :as log]
   [clojure.spec.alpha :as s]
   [xtdb.api :as xt]
   [clojure.set :refer [rename-keys]]))


(def connected-servers (atom {}))

(defmutation register [{:keys [db]} {:keys [server/remote-addr] :as args}]
  {::pc/output []}
                                        ; TODO add existence check for this... 
  (let [id (random-uuid)]
    (when-not (empty? (xt/q db '{:find [(pull e [*])]
                                 :where [[e :type :server/registration]
                                         [e :server/remote-addr remote]]
                                 :in [remote]}
                            remote-addr))
      (throw (Exception. "remote already registered")))
    
    (do
      (xt/submit-tx db/conn [[::xt/put {:xt/id id
                                        :type :server/registration
                                        :server/remote-addr remote-addr}]])
      {:success true :server/id id})))

(defmutation ping [{:keys [db ring/request]} {:keys [server/id] :as args}]
  {::pc/output []}
  (let [{:keys [server/remote-addr]} (xt/pull db [:server/remote-addr] id)]
    (when (or (nil? remote-addr)
              (not= (:remote-addr request) remote-addr))
      (throw (Exception. "server/id invalid"))))
  
  (swap! connected-servers assoc id (java.util.Date.))
  {:received true})

(defresolver servers-registered [{:keys [db]} _]
  {::pc/output [{:servers/registered [:server/id :server/remote-addr :server/last-pinged]}]}
  (def db (xt/db db/conn))
  
  {:servers/registered
   (vec (for [[id remote] (xt/q db '{:find [e remote-addr]
                                     :where [[e :type :server/registration]
                                             [e :server/remote-addr remote-addr ]]})]
          {:server/id id
           :server/remote-addr remote
           :server/last-pinged (@connected-servers id)}))})

(def resolvers [ping register servers-registered])
