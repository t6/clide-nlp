(ns clide.nlp.dev.system
  (:require [clojure.tools.namespace.repl :as repl]
            [com.stuartsierra.component :as component]
            [clide.nlp.assistant.system :as system]))

(def dev-system (system/clide-nlp))

(defn start!
  []
  (alter-var-root #'dev-system component/start)
  nil)

(defn stop!
  []
  (alter-var-root #'dev-system component/stop)
  nil)

(defn restart!
  []
  (stop!)
  (alter-var-root #'dev-system (fn [_] (system/clide-nlp)))
  (repl/refresh :after `start!))
