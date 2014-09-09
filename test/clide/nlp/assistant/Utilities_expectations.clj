(ns clide.nlp.assistant.Utilities-expectations
  (:require [expectations :refer :all])
  (:import (clide.nlp.assistant Utilities$)
           (clide.collaboration Annotations AnnotationType Plain Annotate)
           (scala Tuple2)
           (scala.collection.immutable Nil$)
           (scala.collection JavaConversions$)))

;;; ---------------------------------------------------------------------------
;;; Utility functions to make the tests below easier to read

(defn empty-annotation [] (Annotations. Nil$/MODULE$))
(defn plain [n] (Plain. n))
(defn annotate [n c] (Annotate. n (.toList (.asScalaBuffer JavaConversions$/MODULE$ c))))

(defn ++
  [annotations annotation]
  (Annotations. (.$colon$plus annotations annotation)))

(defn data->annotations
  [data]
  (.dataToAnnotations Utilities$/MODULE$ data))

;;; ---------------------------------------------------------------------------
;;; Expected exceptions with wrong data

(expect IllegalArgumentException
  (from-each [data [[[:plain 0.4]]
                    [[:plain 4] [:annotate 40 [[:Blabla []]]]]
                    [[:plain 1 1 1]]
                    [[:plain 1] [:annotate 1 1 []]]
                    [[:bla 1]]]]
    (data->annotations data)))

(expect ClassCastException
  (data->annotations [[:plain 1] [:annotate 1 1]]))

;;; ---------------------------------------------------------------------------
;;; Empty annotations

(expect (.annotations (empty-annotation))
  (from-each [data [nil []]]
    (data->annotations data)))

;;; ---------------------------------------------------------------------------
;;; Annotations translated from Clojure data and using Clide's classes directly
;;; should be equal

(expect (data->annotations [[:plain 100]
                            [:annotate 50 [[:Output "Hi"]]]
                            [:plain 200]])
  (-> (empty-annotation)
      (++ (plain 100))
      (++ (annotate 50 [(Tuple2. (AnnotationType/Output) "Hi")]))
      (++ (plain 200))
      .annotations))
