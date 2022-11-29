(ns build
  (:require [clojure.tools.build.api :as b]
            [shadow.cljs.devtools.api :as shadow]))

(def lib 'tommy/ftdsl)
(def version (format "1.2.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"})
  (b/delete {:path "resources/public/"}))

(defn jar [_]
  (clean nil)
  (b/compile-clj {:basis basis
                  :src-dirs ["src/main"]
                  :class-dir class-dir})

  (println "done with clj")
  (b/process {:command-args ["npx" "shadow-cljs" "release" ":main"]})
  #_(shadow/release :main {:verbose true})  ;; TODO why doesnt this work??
  (b/copy-dir {:src-dirs ["src/main" "resources"]
               :target-dir class-dir})
  
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'app.server-main})) 
