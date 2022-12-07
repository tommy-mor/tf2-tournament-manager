(ns app.model.tournament
  (:require [xtdb.api :as xt]
            [hato.client :as hc]
            [clojure.java.io :as io]
            [cheshire.core :refer [generate-string parse-string]]
            [clojure.java.shell :refer [sh]]
            [org.httpkit.client :as client]
            [org.httpkit.sni-client :as sni-client]
            [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
            [app.model.mock-database :as db]
            [app.model.challonge :as challonge]
            [taoensso.timbre :as log]))

(alter-var-root #'org.httpkit.client/*default-client* (fn [_] sni-client/default-client))

;; TODO replace this
(def active-tournaments (atom {}))

(defresolver active-tourney [{:keys [db]} {:keys [server/id]}]
  {::pc/input #{:server/id}
   ::pc/output [:server/active-tournament]}
  (def t id)
  ;; TODO not sure if this should query the microservice, or just find it in our db
  ;; right now, find one with :attributes.state "underway" or :attributes.state != "finished"
  {:server/active-tournament (@active-tournaments id)})

(defmutation start-tournament [{:keys [db]} {:keys [server/id]}]
  {::pc/output []}
  (let [tid (challonge/make-tournament)]
    (log/info (challonge/ingest-tournament {:server/id id :tournament/id tid})))
  
  {:server/id id})

(defmutation delete-tournament [{:keys [db]} {sid :server/id
                                              tid :tournament/id}]
  {::pc/output []}

  (def tid tid)
  (def sid sid)
  (let [del #(xt/submit-tx db/conn
                           [[::xt/delete
                             (ffirst (xt/q (xt/db db/conn)
                                           '{:find [e]
                                             :where [[e :server/id sid]
                                                     [e :tournament/id tid]
                                                     [e :type "tournament"]]
                                             :in [sid tid]} sid tid))]])]
    (try (challonge/delete-tournament! tid)
         (catch clojure.lang.ExceptionInfo e
           (def e e)
           (if (-> e ex-data :status #{404})
             (del))
           "epic"))
                                        ; TODO somehow authorize this request
    (del)
    
    {:server/id sid}))

(defresolver serverid->tournament [{:keys [db]} {:keys [server/id]}]
  {::pc/input #{:server/id}
   ::pc/output [{:server/tournaments [:tournament/id]}]}
  {:server/tournaments  (vec (map first (xt/q (xt/db db/conn) '{:find [{:tournament/id e}]
                                                                :where [[e :type "tournament"]
                                                                        [e :tournament/id id]
                                                                        [e :server/id sid]]
                                                                :in [sid]}
                                              id)))})

(def tournament-attrs [:relationships.stations.links.meta.count :attributes.url :attributes.grandFinalsModifier :attributes.notifyUponMatchesOpen :relationships.organizer.data.type :attributes.hideSeeds :attributes.timestamps.startedAt :attributes.signUpUrl :attributes.timestamps.updatedAt :relationships.participants.links.related :attributes.fullChallongeUrl :attributes.thirdPlaceMatch :relationships.matches.data :relationships.matches.links.meta.count :attributes.splitParticipants :tournament/id :relationships.game.data :type :attributes.acceptAttachments :attributes.timestamps.startsAt :relationships.stations.data :relationships.matches.links.related :relationships.stations.links.related :attributes.private :attributes.timestamps.createdAt :attributes.openSignup :attributes.gameName :server/id :attributes.name :attributes.autoAssignStations :attributes.description :links.self :attributes.onlyStartMatchesWithStations :relationships.organizer.data.id :relationships.community.data :attributes.state :attributes.notifyUponTournamentEnds :relationships.participants.links.meta.count :attributes.oauthApplicationId :attributes.liveImageUrl :attributes.tournamentType :attributes.signupCap :relationships.participants.data :relationships.localizedContents.data :attributes.timestamps.completedAt :attributes.sequentialPairings :attributes.checkInDuration])

(defresolver tournament [{:keys [db]} {:keys [tournament/id]}]
  {::pc/input #{:tournament/id}
   ::pc/output tournament-attrs}
  (log/info db)
  (def t (xt/pull db '[*] id))
  t)

(def resolvers [start-tournament delete-tournament
                
                active-tourney tournament serverid->tournament tournament])
