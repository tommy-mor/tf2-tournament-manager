(ns app.model.note
  (:require
   [app.model.session :as session]
   [clojure.string :as str]
   [com.fulcrologic.fulcro.dom :as dom :refer [div ul li p h3 button b textarea pre i]]
   [com.fulcrologic.fulcro.dom.html-entities :as ent]
   [com.fulcrologic.fulcro.dom.events :as evt]
   [com.fulcrologic.fulcro.application :as app]
   [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
   [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
   [com.fulcrologic.fulcro.ui-state-machines :as uism :refer [defstatemachine]]
   [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
   [com.fulcrologic.fulcro.algorithms.merge :as merge]
   [com.fulcrologic.fulcro-css.css :as css]
   [com.fulcrologic.fulcro.algorithms.form-state :as fs]
   
   [com.fulcrologic.fulcro.dom.events :as evt]
   
   [com.fulcrologic.fulcro.data-fetch :as df]
   [taoensso.timbre :as log]))

(js/console.log "ars")

(defmutation select-note [_]
  (action [{:keys [state ref]}]
          (log/info "edit note action" ref)
          (swap! state assoc-in [:component/id :notes :ui/currently-editing] ref)))

(defmutation edit-note [{:note/keys [id text]}]
  
  (action [{:keys [state]}]
          (log/info "submit note action, " id text)
          
          (reset! state
                  (-> @state
                      (assoc-in [:note/id :new :note/text] "")
                      (assoc-in [:component/id :notes :ui/currently-editing] [:note/id :new]))))
  
  (remote [env] true))

(defsc Note [this {:keys [:note/text :note/id]}]
  {:query [:note/id :note/text :note/modified :note/created]
   :ident :note/id}
  (div :.ui.container.segment
       (i :.edit.outline.icon {:style {:float "right"
                                       :cursor "pointer"}
                               :onClick (fn []
                                          (comp/transact! this [(select-note)]))})
       (pre text)))


(def ui-note (comp/factory Note {:keyfn :note/id}))

(defsc NoteEditor [this {:keys [:note/text :note/id] :as args}]
  {:query [:note/id :note/text :note/modified :note/created]
   :ident :note/id
   :initial-state {:note/id :new :note/text ""}}
  
  (div :.ui.container.segment
       (div :.ui.form
            
            (div :.field
                 (textarea {:rows 4
                            :value text
                            :onChange (fn [evt] (m/set-string! this :note/text :event evt))}))
            (div :.field
                 (button :.ui.button
                         {:onClick (fn [] (comp/transact! this [(edit-note args)]))}
                         "submit")))))

(def ui-note-editor (comp/factory NoteEditor))

(defsc NotePage [this {:keys [:notes/all :ui/currently-editing]}]
  {:query [{:notes/all (comp/get-query Note)}
           {:ui/currently-editing (comp/get-query NoteEditor)}]
   :ident (fn [] [:component/id :notes])
   :route-segment ["notes"]
   :initial-state {:ui/currently-editing {:note/text "test"}}
   :will-enter (fn [app route-params]
                 (log/info "entering notes route" route-params app)
                 (dr/route-deferred [:component/id :notes]
                                    (fn load []
                                      (df/load! app
                                                :notes
                                                NotePage
                                                {:post-mutation `dr/target-ready
                                                 :post-mutation-params
                                                 {:target [:component/id :notes]}}))))}
  (div
   (ui-note-editor currently-editing)
   (div :.ui.container.segment
        (h3 (str "all notes"))
        (doall (for [note all]
                 (ui-note note))))))
