(ns clide.nlp.knowledge-base.expectations-utils
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer (run-db*)]
            [t6.snippets.core :as snippets]
            [t6.snippets.nlp :as nlp]))

(defn db-with-text
  [text]
  (snippets/create {:text text :pipeline {:type :corenlp}}))

(defmacro run-with-text*
  "Runs a core.logic query with a knowledge base initialized with `text`.
   Takes the same parameters as `clojure.core.logic/run*` apart from `text`."
  [text & body]
  `(nlp/with-db (db-with-text text)
     (run* ~@body)))
