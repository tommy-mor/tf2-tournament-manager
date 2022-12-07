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
                    tournament/resolvers
                    index-explorer ])

(def update-db-after-mutation-plugin
  {::pcr/wrap-mutate
   (fn sample-mutate-wrapper [mutate]
     (fn [env ast]
       (log/info "running mutation in " env " on " ast)
       (mutate env ast)))})

(defn preprocess-parser-plugin
  "Helper to create a plugin that can view/modify the env/tx of a top-level request.

  f - (fn [{:keys [env tx]}] {:env new-env :tx new-tx})

  If the function returns no env or tx, then the parser will not be called (aborts the parse)"
  [id f]
  {::p.plugin/id id
   ::pcr/wrap-resolve
   (fn transform-parser-out-plugin-external [parser]
     (fn transform-parser-out-plugin-internal [env tx]
       (let [{:keys [env tx] :as req} (f {:env env :tx tx})]
         (if (and (map? env) (seq tx))
           (parser env tx)
           {}))))})

(defn log-requests [{:keys [env tx] :as req}]
  (log/debug "Pathom transaction:" (pr-str tx))
  req)

(defn build-parser [db-connection]
  (let [plugins [(pbip/env-wrap-plugin #(assoc %
                                               :db (xt/db db-connection)
                                               :config config))]
        env (->
             {::p.a.eql/parallel? true
              :com.wsscode.pathom3.error/lenient-mode? true}
             
             (p.plugin/register plugins)
             (pci/register all-resolvers))
        trace? (not (nil? (System/getProperty "trace")))]
    (fn parser [{:keys [ring/request] :as env'} tx]
      @(p.a.eql/process (merge env env') (cond-> tx trace?
                                      (conj :com.wsscode.pathom3/trace))))))

(defstate parser
  :start (build-parser db/conn))

