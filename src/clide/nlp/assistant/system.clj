(ns clide.nlp.assistant.system
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.core.async :as async]
            [clide.nlp.logging :as log]
            [com.stuartsierra.component :as component]
            [clide.nlp.assistant.core :as assistant]
            [clide.nlp.assistant.reconciler :as r]
            [t6.snippets.nlp :as nlp]
            [t6.from-scala.core :refer ($) :as $])
  (:import (org.apache.tools.ant.util JavaEnvUtils)
           (clide.nlp.assistant AsyncAssistantServer
                                AssistantControlDelegate
                                ReconcilerStateTracker
                                Reconciler AsyncAssistantBehavior)
           (clide.assistants AssistantServer)
           (scala.collection JavaConversions)
           (scala.collection.immutable Map)))

(defrecord ClideWeb [server]
  component/Lifecycle

  (start [this]
    (log/info "Starting clide-web / clide-core")
    (assoc this
      :server
      (let [java       (JavaEnvUtils/getJreExecutable "java")
            vm-options ["-XX:MaxGCPauseMillis=50"]
            args       (concat [java] vm-options ["-Dhttp.port=14000" "clide.nlp.util.WebStarter"])
            pb         (ProcessBuilder. (into-array String args))]
        (log/info "clide-web VM options:" (str/join " " vm-options))
        (log/info "clide-web classpath:" (System/getProperty "java.class.path"))
        (doto (.environment pb)
          (.put "CLASSPATH" (System/getProperty "java.class.path")))
        (.start pb))))

  (stop [this]
    (log/info "Stopping clide-web / clide-core")
    (when server
      (doto server
        .destroy
        .waitFor)
      (.delete (io/file "RUNNING_PID")))
    (assoc this :server nil)))

(defn map->scala-map
  [m]
  ($ ($ Map/empty) ++ (JavaConversions/mapAsScalaMap m)))

(defrecord Assistant [assistant in-chan annotation-delay]
  component/Lifecycle

  (start [this]
    (log/info "Starting NLP assistant")
    (let [streams    (map->scala-map (assistant/annotation-stream-descriptions))
          reconciler (Reconciler.)
          server     (AsyncAssistantServer. reconciler in-chan streams)]
      (.startup server)
      (async/go
        ;; Wait until we have a behavior instance
        (let [behavior (async/<! in-chan)
              control  (.control behavior)]
          ;; force loading of CoreNLP pipeline
          #_(pipeline/pipeline)

          (let [watch-for-changes!
                (fn [& _]
                  (async/go
                    (if-let [[file state chunk point]
                             ($/view (.lastAnnotationPoint behavior))]
                      (async/>! in-chan
                                ($/tuple file
                                         state
                                         (assoc chunk
                                           :annotations
                                             (r/annotate state chunk))
                                           point)))))]
            ;; Register watches on the triple queries to reannotate the
            ;; last annotated chunk whenever the triple builders change
            (doseq [query (keys @nlp/triple-query-registry)]
              (nlp/watch-query query ::refresh-annotations watch-for-changes!))

            ;; Register watches on the annotation stream handlers and update
            ;; the last annotated chunk whenever they change
            (doseq [[_ {:keys [annotator]}] assistant/annotation-streams]
              (add-watch annotator :refresh-annotations watch-for-changes!)))

          (assistant/assistant-loop control
                                    in-chan
                                    annotation-delay)))
      (assoc this
        :assistant {:server  server
                    :channel in-chan
                    :streams streams})))

  (stop [this]
    (when-let [{:keys [server channel loop]} assistant]
      (log/info "Stopping NLP assistant")
      (async/close! channel)
      #_(async/close! loop)

      ;; unwatch triple builders
      #_(doseq [builder t/*triple-builders*]
        (remove-watch builder :refresh-annotations))

      ;; unwatch annotation stream handlers
      (doseq [[_ {:keys [annotator]}] assistant/annotation-streams]
        (remove-watch annotator :refresh-annotations))

      (.shutdown server))
    this))

(defrecord ClideNLP [assistant clide-web]
  component/Lifecycle

  (start [this]
    (component/start-system this [:assistant :clide-web]))

  (stop [this]
    (component/stop-system this [:clide-web :assistant])))

(defn clide-nlp
  []
  (map->ClideNLP
   {:assistant  (map->Assistant {:in-chan          (async/chan)
                                 :annotation-delay 500})
    :clide-web  (->ClideWeb nil)}))
