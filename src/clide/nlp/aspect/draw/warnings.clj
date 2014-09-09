(ns clide.nlp.aspect.draw.warnings
  (:require [clide.nlp.logging :as log]
            [plumbing.core :refer (fnk)]))

#_(def WarningTypes
  (s/enum :node-has-no-label
          :named-edge-has-no-end-node
          :named-edge-has-no-start-node
          :invalid-edge-length-unit
          :invalid-edge-length-format
          :no-edge-length-or-unit-found
          :named-edge-has-no-label
          :edge-is-specified-multiple-times))


;;; ---------------------------------------------------------------------------
;;; Multimethods for extracting information about warnings

(defmulti word-maps "Returns the word maps that a warning is about."
  (fn [[type _]] type)
  :default ::default)

(defmulti description "Returns a human readable description of the warning."
  (fn [[type _]] type)
  :default ::default)

(defmulti suggestions "Returns suggestions what to do about a warning."
  (fn [[type _]] type)
  :default ::default)

;;; ---------------------------------------------------------------------------
;;; Default implementations

(defmethod word-maps ::default
  [[type _]]
  (log/warn "missing word-maps implementation for warning" type)
  [])

(defmethod description ::default
  [[type _]]
  (log/warn "missing description implementation for warning" type)
  ["missing description"])

(defmethod suggestions ::default
  [[type _]]
  (log/warn "missing suggestions implementation for warning" type)
  [])

;;; ---------------------------------------------------------------------------
;;; Utility functions

(defn edge-word-maps
  [{:keys [from to] :as edge}]
  (concat (:group from) (:group to)))

;;; ---------------------------------------------------------------------------

(defmethod word-maps :node-has-no-label
  [[_ node]]
  (:group node))

(defmethod description :node-has-no-label
  [[type node]]
  ["Node" [:node (:symbol node)] "has no label"])

;;; ---------------------------------------------------------------------------

(defmethod word-maps :named-edge-has-no-label
  [[_ edge]]
  (edge-word-maps edge))

(defmethod description :named-edge-has-no-label
  [[type edge]]
  ["Edge" [:edge (:symbol edge)] "has no label"])

;;; ---------------------------------------------------------------------------

(defmethod word-maps :named-edge-has-no-end-node
  [[_ triple]]
  (get-in triple [:subject :group]))

(defmethod description :named-edge-has-no-end-node
  [[type triple]]
  ["Edge" [:edge (-> triple :subject :symbol)] "has no end node"])

;;; ---------------------------------------------------------------------------

(defmethod word-maps :named-edge-has-no-start-node
  [[_ triple]]
  (get-in triple [:subject :group]))

(defmethod description :named-edge-has-no-start-node
  [[type triple]]
  ["Edge" [:edge (-> triple :subject :symbol)] "has no start node"])

;;; ---------------------------------------------------------------------------

(defmethod word-maps :invalid-edge-length-format
  [[_ [edge length]]]
  (:group length))

(defmethod description :invalid-edge-length-format
  [[type [edge length]]]
  ["Edge" [:edge (:symbol edge)] "length"
   [:quote (-> length :group (nth 0) :lemma)] "is not an integer."])

;;; ---------------------------------------------------------------------------

(defmethod word-maps :invalid-edge-length-unit
  [[_ [edge unit]]]
  (:group unit))

(defmethod description :invalid-edge-length-unit
  [[type [edge unit]]]
  ["Unit" [:quote (-> unit :group (nth 0) :lemma)]
   "for edge" [:edge (:symbol edge)] "is unknown"])

(defmethod suggestions :invalid-edge-length-unit
  [[type _]]
  [["Use" [:quote "cm"]]])

;;; ---------------------------------------------------------------------------

(defmethod word-maps :no-edge-length-or-unit-found
  [[_ edge]]
  (edge-word-maps edge))

(defmethod description :no-edge-length-or-unit-found
  [[type edge]]
  ["Edge" [:edge (:symbol edge)] "has no length"])

(defmethod suggestions :no-edge-length-or-unit-found
  [[type _]]
  [["Add a sentence like"
   [:quote "Edge B is 5 cm long."]
   "or"
   [:quote "Node A and Node B have a distance of 2 cm."]]])

;;; ---------------------------------------------------------------------------

(defmethod word-maps :edge-is-specified-multiple-times
  [[type edges]]
  (mapcat edge-word-maps edges))

(defmethod description :edge-is-specified-multiple-times
  [[type edges]]
  ["Edge" [:edge (-> edges first :symbol)] "is specified multiple times"])

(defmethod suggestions :edge-is-specified-multiple-times
  [[type _]]
  [["Check the sentences below"]])

;;; ---------------------------------------------------------------------------

(defmethod word-maps :unused-nodes
  [[type nodes]]
  (distinct (mapcat :group nodes)))

(defmethod description :unused-nodes
  [[type nodes]]
  ["You specified nodes that are not used by any edge."])

(defmethod suggestions :unused-nodes
  [[type _]]
  [["Check for typos. Maybe you mistyped a node label?"]
   ["Remove the affected sentences or mentions."]])


