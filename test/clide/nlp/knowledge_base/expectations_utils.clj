(ns clide.nlp.knowledge-base.expectations-utils
  (:refer-clojure :exclude [==])
  (:require [clojure.core.logic :refer (run-db*)]
            [tobik.snippets.nlp :as nlp]))

(defn db-with-text
  [text]
  #_(let [document (annotate (pipeline) text)
        chains   (coref-chain-map document)
        graphs   (semantic-graph-seq document)
        db       (nlp-db {} graphs chains)
        triples  (reify-triples (grouped-triples db))]
    (reified-triples->pldb db triples)))

(defmacro run-with-text*
  "Runs a core.logic query with a knowledge base initialized with `text`.
   Takes the same parameters as `clojure.core.logic/run*` apart from `text`."
  [text & body]
  `(run-db*
     (db-with-text ~text)
     ~@body))
