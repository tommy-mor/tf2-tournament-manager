(ns app.model.mge-servers
  (:require
   [app.model.mock-database :as db]
   [app.model.session :as session]
   [com.fulcrologic.guardrails.core :refer [>defn => | ?]]
   [com.wsscode.pathom3.connect.operation :as pco :refer [defresolver defmutation]]
   [taoensso.timbre :as log]
   [clojure.spec.alpha :as s]
   [xtdb.api :as xt]
   [clojure.set :refer [rename-keys]]
   [org.httpkit.client :as client]
   [cheshire.core :as json]))


(def connected-servers (atom {}))

(defn clear-registrations []
  "for testing"
  (doall (for [[e] (xt/q (xt/db db/conn) '{:find [e] :where [[e :type :server/registration]]})]
           (xt/submit-tx db/conn [[::xt/delete e]]))))

(defmutation register [{:keys [db]} {:keys [server/game-addr server/api-addr] :as args}]
  {::pco/output []}
                                        ; TODO add existence check for this... 
  (let [id (random-uuid)]
    (when-not (empty? (xt/q db '{:find [(pull e [*])]
                                 :where [[e :type :server/registration]
                                         [e :server/game-addr remote]]
                                 :in [remote]}
                            game-addr))
      (throw (Exception. "remote already registered")))
    
    (do
      (xt/submit-tx db/conn [[::xt/put {:xt/id id
                                        :type :server/registration
                                        :server/game-addr game-addr
                                        :server/api-addr api-addr}]])
      {:success true :server/id id})))

(defmutation ping [{:keys [db ring/request]} {:keys [server/id] :as args}]
  {::pco/output []}
  (def args args)
  (def id (:server/id args))
  (def db (xt/db db/conn))
  (let [{:keys [server/api-addr]} (xt/pull db '[*] id)]
    (def api-addr api-addr)
    (def request request)
    (when (or (nil? api-addr)
              (not= (:remote-addr request)
                    (.getHost (java.net.URI. api-addr))))
      (throw (Exception. "server/id invalid"))))
  
  (swap! connected-servers assoc id (java.util.Date.))
  {:received true})

(defresolver servers-registered [{:keys [db]} _]
  {::pco/output [{:servers/registered [:server/id]}]}
  {:servers/registered
   (vec (for [[id] (xt/q db '{:find [e]
                                     :where [[e :type :server/registration]]})]
          {:server/id id}))})

(defresolver single-server [{:keys [db]} {:keys [server/id]}]
  {::pco/input [:server/id]
   ::pco/output [:server/id :server/game-addr :server/api-addr :server/last-pinged]}
  (let [[id game api] (first (xt/q db '{:find [e game api]
                                      :where [[e :xt/id id]
                                              [e :type :server/registration]
                                              [e :server/game-addr game]
                                              [e :server/api-addr api]]
                                      :in [id]}
                                 id))]
    {:server/id id
     :server/game-addr game
     :server/api-addr api
     :server/last-pinged (@connected-servers id)}))


(defn request [m]
  (json/parse-string (slurp (:body @(client/request m)))
                     keyword))

(defresolver server-players [{:keys [db]} {:keys [server/api-addr]}]
  {::pco/input [:server/api-addr]
   ::pco/output [:server/players]}

  {:server/players (request {:method :get
                             :url (str api-addr "/api/players") })})

(def resolvers [ping register servers-registered single-server server-players])
