(ns clide.nlp.assistant.main
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [clojure.java.browse :refer (browse-url)]
            [seesaw.core :as seesaw]
            [seesaw.widgets.log-window :as log-window]
            [seesaw.dev :refer (debug!)]
            [clide.nlp.logging :as log]
            [clide.nlp.assistant.system :refer (clide-nlp)])
  (:import (java.awt.event WindowAdapter)
           (org.apache.log4j WriterAppender Logger Level EnhancedPatternLayout)))

(defn start-system
  [log-appender system]
  (.addAppender (Logger/getRootLogger) log-appender)
  (log/log-capture! 'clide.nlp.assistant.main :info :info)
  (swap! system component/start))

(defn stop-system
  [log-appender system]
  (swap! system component/stop))

(defn log-window
  [system]
  (let [log-win        (log-window/log-window :auto-scroll? true)
        info-win       (log-window/log-window :auto-scroll? true)
        writer         (proxy [java.io.Writer] []
                         (close [])
                         (flush [])
                         (write [s & _]
                           (log-window/log log-win s)))
        layout         (EnhancedPatternLayout. "%d %-5p [%t]: %m%n")
        appender       (WriterAppender. layout writer)
        quit-button    (seesaw/button :text "Quit" :listen [:action (fn [_] (System/exit 0))])
        url-button     (seesaw/button :text "Open Clide" :enabled? false)
        toolbar        (seesaw/toolbar :items [quit-button :separator url-button])
        frame          (seesaw/frame :width 800
                                     :height 600
                                     :title "clide-nlp Launcher"
                                     :visible? true
                                     :content (seesaw/border-panel
                                               :north toolbar
                                               :center (seesaw/top-bottom-split
                                                        (seesaw/scrollable info-win)
                                                        (seesaw/scrollable log-win)
                                                        :divider-location 200)))
        frame-visible? (async/chan)]
    (log-window/log info-win "Welcome!
Please wait while Clide and clide-nlp are getting ready...

")
    (async/go
     (async/<! frame-visible?)
     (seesaw/invoke-later
      (log-window/log info-win "-> Starting clide-nlp...\n"))
     (start-system appender system)
     (async/<! (async/timeout 10000))
     (seesaw/invoke-later
      (log-window/log info-win "-> Ready on http://localhost:14000

-> Username: clide-nlp
-> Password: clide-nlp

-> Open '00-README.txt' in the 'Example' project for an introduction

-> Note: This demo uses an in-memory database, so any change you do is lost across restarts.
")
      (seesaw/config! url-button :enabled? true)))
    (.addWindowListener frame
                        (proxy [WindowAdapter] []
                          (windowActivated [event] (async/go (async/>! frame-visible? true)))))
    (seesaw/listen url-button :action (fn [_] (browse-url "http://localhost:14000")))
    frame))

(defn -main
  []
  (seesaw/native!)
  (debug!)
  (let [system (atom (clide-nlp))]
    (.. Runtime getRuntime (addShutdownHook (Thread. (fn [] (swap! system component/stop)))))
    (log-window system)))
