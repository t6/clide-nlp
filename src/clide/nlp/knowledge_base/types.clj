(ns clide.nlp.knowledge-base.types
  "Some basic type annotations for the knowledge base.
   These are not complete but allow for some basic type checking.
   Note that core.logic queries can't be type checked at the moment."
  (:require [clojure.core.typed :as t :refer (All Any)]))

(t/defalias
  KnowledgeBase
  (t/Map String (t/Map t/Keyword Any)))

(t/defalias
  Relation
  [Any * -> Any])

(t/defalias
  Goal
  [Any * -> Any])

(t/ann ^:no-check clojure.math.combinatorics/selections
       (All [x] [(t/Seq x) t/Int -> (t/Seq (t/Seq x))]))

(t/ann ^:no-check clojure.core.logic/*logic-dbs*
       (t/Vec KnowledgeBase))

(t/ann ^:no-check clojure.core.logic.pldb/db-fact
       [KnowledgeBase Relation Any * -> KnowledgeBase])

(t/ann ^:no-check clojure.core.logic.pldb/empty-db
       KnowledgeBase)
