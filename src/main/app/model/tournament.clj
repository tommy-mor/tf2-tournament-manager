(ns app.model.tournament
  (:require [xtdb.api :as xt]
            [hato.client :as hc]
            [clojure.java.io :as io]
            [cheshire.core :refer [generate-string parse-string]]
            [clojure.java.shell :refer [sh]]
            [org.httpkit.client :as client]
            [org.httpkit.sni-client :as sni-client]
            [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]))

(alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))

(def client_id (:client_id (clojure.edn/read-string (slurp "challonge.edn"))))
(def client_secret (:client_secret (clojure.edn/read-string (slurp "challonge.edn"))))
(def redirect_uri "https://oauth.pstmn.io/v1/callback")

(defresolver serverid->tournament [{:keys [db]} {:keys [server/id]}]
  {::pc/input #{:server/id}
   ::pc/output [{:server/tournament [:tournament/id :tournament/name]}]}
  {:server/tournament {:tournament/id "123"}})

;; TODO replace this
(def active-tournaments (atom {}))

(defresolver active-tourney [{:keys [db]} {:keys [server/id]}]
  {::pc/input #{:server/id}
   ::pc/output [:server/active-tournament]}
  (def t id)
  {:server/active-tournament (@active-tournaments id)})

(defmutation start-tournament [{:keys [db]} {:keys [server/id]}]
  {::pc/output []}
  (swap! active-tournaments assoc id "123")
  {:server/id id})




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

(def tokens (delay (refresh-tokens)))

(comment (hc/get "https://api.challonge.com/v2/tournaments.json"
                 (make-options @tokens)))




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

(def resolvers [serverid->tournament start-tournament active-tourney])
