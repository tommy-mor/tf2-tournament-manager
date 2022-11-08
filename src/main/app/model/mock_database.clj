(ns app.model.mock-database
  (:require
   [xtdb.api :as xt]
   [clojure.java.io :as io]
   [mount.core :refer [defstate]]))

(defn start-xtdb! []
  (letfn [(kv-store [dir]
            {:kv-store {:xtdb/module 'xtdb.rocksdb/->kv-store
                        :db-dir (io/file dir)
                        :sync? true}})]
    (xt/start-node
     {:xtdb/tx-log (kv-store "data/dev/tx-log")
      :xtdb/document-store (kv-store "data/dev/doc-store")
      :xtdb/index-store (kv-store "data/dev/index-store")})))





;; In datascript just about the only thing that needs schema
;; is lookup refs and entity refs.  You can just wing it on
;; everything else.
(defstate conn :start (start-xtdb!) :stop (.close conn))
