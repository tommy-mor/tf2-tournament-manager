(ns app.server-components.pathom
  (:require
    [mount.core :refer [defstate]]
    [taoensso.timbre :as log]
    [com.wsscode.pathom3.connect.operation :as pco]
    [com.wsscode.pathom3.connect.runner :as pcr]
    [com.wsscode.pathom3.connect.indexes :as pci]
    [com.wsscode.pathom3.connect.built-in.plugins :as pbip]
    [com.wsscode.pathom3.interface.async.eql :as p.a.eql]
    [com.wsscode.pathom3.plugin :as p.plugin]
    
    [clojure.core.async :as async]
    [app.model.account :as acct]
    [app.model.session :as session]
    [app.model.mge-servers :as mge]
    [app.model.tournament :as tournament]
    [app.model.note :as note]
    [app.server-components.config :refer [config]]
    [app.model.mock-database :as db]

    [xtdb.api :as xt]
    [clojure.walk :refer [postwalk]]))

(pco/defresolver index-explorer [env _]
  {::pco/input  [:com.wsscode.pathom.viz.index-explorer/id]
   ::pco/output [:com.wsscode.pathom.viz.index-explorer/index]}
  {:com.wsscode.pathom.viz.index-explorer/index
   (-> (get env ::pco/indexes)
       (update ::pco/index-resolvers #(into {} (map (fn [[k v]] [k (dissoc v ::pco/resolve)])) %))
       (update ::pco/index-mutations #(into {} (map (fn [[k v]] [k (dissoc v ::pco/mutate)])) %)))}
  {:com.wsscode.pathom.viz.index-explorer/index
   (-> env
       (select-keys [
                     ::pci/index-attributes
                     ::pci/index-resolvers
                     ::pci/index-mutations
                     ::pci/index-io
                     ::pci/index-oir]))})


(def all-resolvers [acct/resolvers
                    session/resolvers
                    note/resolvers
                    mge/resolvers
                    tournament/resolvers])

(def log-resolve-plugin
  {::p.plugin/id `log-resolve-plugin
   ::pcr/wrap-resolve
   (fn resolve-wrapper [resolve]
     (fn [env input]
       (log/debug "pathom transaction" input)
       (resolve env input)))})

(defn build-parser [db-connection]
  (let [plugins [log-resolve-plugin
                 (pbip/env-wrap-plugin #(assoc %
                                               :db (atom (xt/db db-connection))
                                               :config config))
                 pbip/mutation-resolve-params]
        env (->
             {::p.a.eql/parallel? true
              :com.wsscode.pathom3.error/lenient-mode? true}
             
             (pci/register all-resolvers)
             (p.plugin/register plugins))
        trace? (not (nil? (System/getProperty "trace")))]
    (fn parser [{:keys [ring/request] :as env'} tx]
      (def tx tx)
      (let [r @(p.a.eql/process (merge env env') (cond-> tx trace?
                                                        (conj :com.wsscode.pathom3/trace)))]
        (def r r)
        "TODO plugin that prunes/logs errors here!"
        (comment (-> r
                     vals first
                     vals first
                     Throwable->map
                     :trace) )
        r))))

(defstate parser
  :start (build-parser db/conn))

