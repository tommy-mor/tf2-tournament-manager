
(ns app.model.challonge
  (:require [hato.client :as hc]
            [cheshire.core :refer [generate-string]]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [xtdb.api :as xt]
            [clojure.java.io :as io]
            [app.model.mock-database :as db]))

;; TODO this wont work on build server...

(def client_id (or (System/getenv "CI")
                   (:client_id (clojure.edn/read-string (slurp "challonge.edn")))))
(def client_secret (or
                    (System/getenv "CI")
                    (:client_secret (clojure.edn/read-string (slurp "challonge.edn")))))
(def redirect_uri "https://oauth.pstmn.io/v1/callback")
(def api-root "https://api.challonge.com/v2")

(defn make-options [tokens] {:headers {"Authorization-Type" "v2"}
                             :oauth-token (:access_token tokens)
                             :content-type "application/vnd.api+json"
                             :accept :json
                             :as :json})

(defn refresh-tokens []
  (when (not (.exists (io/file "token.edn")))
    (throw (Exception. "can only refresh token, not create new one")))

  (let [tokens (clojure.edn/read-string (slurp "token.edn"))]
    
    (try
      (hc/get "https://api.challonge.com/v2/me.json" (make-options tokens))
      tokens
      (catch clojure.lang.ExceptionInfo e
        (def e e)
        e
        (let [req (hc/post "https://api.challonge.com/oauth/token"
                           {:form-params {:grant_type "refresh_token"
                                          :client_id client_id
                                          :refresh_token (:refresh_token tokens)
                                          :redirect_uri redirect_uri}
                            :as :json})]
          (spit "token.edn" (:body req))
          (clojure.edn/read-string (slurp "token.edn")))))))


(def options (delay (make-options (refresh-tokens))))

(comment (hc/get "https://api.challonge.com/v2/tournaments.json"
                 (make-options @tokens)))

(defn make-tournament []
  (-> (hc/post (str api-root "/tournaments.json")
               (merge @options
                      {:body
                       (generate-string
                        {:data {:type "tournaments"
                                :attributes
                                {:name "mge weekly tournament"
                                 :tournament_type "double elimination"}}})}))
      :body :data :id))

(defn delete-tournament! [tid]
  (hc/delete (str api-root "/tournaments/" tid ".json") @options))

(defn participants-api-url [tourney-id]
  (str api-root "/tournaments/" tourney-id "/participants.json"))

(defn add-participant [tourney-id {:keys [name] :as participant}]
  (let [payload {:data
                 {:type "Participant"
                  :attributes
                  {:name name
                   :misc (str name "_steamid")}}} ]
    
    (hc/post (participants-api-url tourney-id)
             (merge @options
                    {:body (generate-string payload)}))))

(defn add-all-players [tourney-id players]
  (let [payload {:data
                 {:type "Participants"
                  :attributes
                  {:participants
                   (vec (for [{:keys [name steamid]} players]
                          {:name name :misc steamid
                           :seed 1}))}}} ]
    
    (hc/post (str api-root "/tournaments/" tourney-id "/participants/bulk_add.json")
             (merge @options
                    {:body (generate-string payload)}))))

(defn change-tourney-status [tourney-id new-status]
  (assert (#{"start" "process_checkin" "abort_checkin" "open_predictions" "finalize" "reset"} new-status))
  (let [payload {:data
                 {:type "TournamentState"
                  :attributes
                  {:state new-status}}}]
    
    (hc/put (str api-root "/tournaments/" tourney-id "/change_state.json")
            (merge @options
                   {:body (generate-string payload)}))))






(defn flatten-keys* [a ks m]
  "http://blog.jayfields.com/2010/09/clojure-flatten-keys.html"
  (if (map? m)
    (reduce into (map (fn [[k v]] (flatten-keys* a (conj ks k) v)) (seq m)))
    (assoc a (keyword (clojure.string/join "." (map name ks))) m)))

(defn flatten-keys [m] (flatten-keys* {} [] m))

(defn ingest [tid sid req]
  (let [payload (->> req
                     :body
                     ((juxt :data :included))
                     (map (fn [res] (cond (map? res) [res] (vector? res) res)))
                     flatten
                     (map flatten-keys)
                     (map #(assoc % :tournament/id tid))
                     (map #(assoc % :server/id sid))
                     (map #(assoc (dissoc % :id) :xt/id (:id %))))]
    (def payload payload)
    (xt/await-tx db/conn (xt/submit-tx db/conn (for [doc payload]
                                                 [::xt/put doc])))))

(defn ingest-tournament [{sid :server/id
                          tid :tournament/id}]
  (ingest tid sid
          (hc/get (str api-root "/tournaments/" tid ".json") @options)))


(defn get-active-matches [tourney-id]
  (xt/q (xt/db db/conn) '{:find [steamid1 steamid2]
                       :where [[match :type "match"]
                               [match :tournament/id tourney-id]
                               [match :attributes.state "open"]
                               [match :relationships.player1.data.id p1]
                               [match :relationships.player2.data.id p2]
                               [p1 :attributes.misc steamid1]
                               [p2 :attributes.misc steamid2]]
                       :in [tourney-id]}
        tourney-id))



(comment
  (defonce tourney-id (make-tournament))
  (def server-id #uuid "e42f262a-2506-4653-a6f4-789164f91b74")
  (ingest tourney-id server-id
          (hc/get (str api-root "/tournaments/" tourney-id ".json") @options))
  
  
  )


















(comment
  (def scopes ["me"
               "tournaments:read"
               "tournaments:write"
               "matches:read"
               "matches:write"
               "participants:read"
               "participants:write"
               "attachments:read"
               "attachments:write"
               "communities:manage"])

  (refresh-tokens)
  (comment
    (defn comunity-oauth-url []
      "to be pasted into browser once to give my application access to the tf2 community"
      (str "https://api.challonge.com/oauth/authorize?scope=" (clojure.string/join " " scopes)
           "&client_id=" client_id
           "&redirect_uri=" redirect_uri
           "&response_type=code"))
    
    "https://api.challonge.com/oauth/authorize?scope=me tournaments:read tournaments:write matches:read matches:write participants:read participants:write attachments:read attachments:write communities:manage&client_id=3bd276c270e74d5e90d7482425ee86d68a99898b315379a07432787fca67cfc1&redirect_uri=https://oauth.pstmn.io/v1/callback&response_type=code"
    (def code "6cd00e4fd1fcd36e543c6b7df49ddf6911fab63295031009f0155c78aad99c79")
    (defn get-oauth-token []
      (-> (hc/post "https://api.challonge.com/oauth/token"
                   {:form-params {:grant_type "client_credentials"
                                  :client_id client_id
                                  :client_secret client_secret}
                    :as :json})
          :body))
    
    (defn get-oauth-token-2 [code]
      (parse-string (:body @(client/request
                             {:method :post
                              :url "https://api.challonge.com/oauth/token"
                              :form-params {:code code
                                            :client_id client_id
                                            :grant_type "authorization_code"
                                            :redirect_uri redirect_uri}}))))

    (spit "token.edn" (pr-str l))
    
    (def swage (get-oauth-token-2 code))
    (def tokens swage)
    (spit "token.edn" (pr-str tokens))))
