(ns clide.nlp.aspect.draw-expectations
  (:refer-clojure :exclude [== record?])
  (:require [expectations :refer :all :exclude (fail)]
            [clojure.core.logic :refer :all :exclude (run*)]
            [clide.nlp.knowledge-base.expectations-utils :refer (db-with-text)]
            [t6.snippets.nlp :as nlp]
            [clide.nlp.aspect.draw :refer :all]
            [clide.nlp.aspect.draw.warnings :as warnings]
            [plumbing.core :refer (for-map)]))

;;; --------------------------------------------------------------------------
;;; The text these expectations use

(def texts
  {:named-edge-no-end-node
   "Edge B starts at node B ."

   :named-edge-no-start-node
   "Edge B goes to node A ."

   :named-edge-complete
   "Edge B goes to node A and starts at node B ."

   :unnamed-edge-complete
   "Node A and node B are connected."

   :named-edge-distance
   "Edge B goes to node A and starts at node B . Edge B is 5 cm long."

   :unnamed-edge-distance
   "Node A and node B are connected. Node C and node B have a distance of 3 cm."

   :named-edge-distance-unit-invalid
   "Edge B goes to node A and starts at node B . Edge B is 5 foos long."

   :unnamed-edge-distance-unit-invalid
   "Node A and node B are connected. Node A and node B have a distance of 5 foos."

   :named-edge-distance-length-invalid
   "Edge B goes to node A and starts at node B . Edge B is 4.5 cm long."

   :unnamed-edge-distance-length-invalid
   "Node A and node B are connected. Node A and node B have a distance of 4.5 cm."

   :named-edge-distance-unit-length-invalid
   "Node A and node B are connected with a distance of 4.5 foos."

   :unnamed-edge-distance-unit-length-invalid
   "Edge B goes to node A and starts at node B . Edge B is 4.5 foos long."

   :all
   "Edge B goes to node A and starts at node B .
   Edge B goes to node F and starts at node G .
   Node C and node B have a distance of 3 cm.
   Node B and node D are connected as are node B and node C .
   Node F and Node C have a distance of 4 cm.
   Node A and node B have a distance of 5 cm.
   Edge B is 4.5 cm long. Edge B is 5 cm long."})

;;; --------------------------------------------------------------------------
;;; Expectations setup and helpers

(def dbs)

(defn create-dbs
  "Create the NLP knowledge base from the text before running the tests,
  instead of while running a test. This speeds up the tests."
  {:expectations-options :before-run}
  []
  (alter-var-root #'dbs #(for-map [[k text] %2] k (db-with-text text)) texts))

(defmacro run*
  [k & body]
  `(binding [*warnings* (atom [])]
     (nlp/with-db (dbs ~k)
       (let [result# (doall (run-nc* ~@body))]
         {:result   (vec result#)
          :warnings @*warnings*}))))

;;; --------------------------------------------------------------------------
;;; Expectations

;; check named edge search
(expect (more-of {:keys [warnings result]}
          (more-> "B"   :label
                  false :unnamed?)
          (in result)

          1
          (count (distinct warnings))

          :no-edge-length-or-unit-found
          (ffirst (distinct warnings)))
  (run* :named-edge-complete [q] (edgeo q)))

(expect
  (more-of {:keys [warnings result]}
    #{:named-edge-has-no-start-node}
    (set (map first warnings)))

  (run* :named-edge-no-start-node [q] (edgeo q)))

(expect (more-of {:keys [warnings result]}
          #{:named-edge-has-no-end-node}
          (set (map first warnings))

          empty?
          result)
  (run* :named-edge-no-end-node [q] (edgeo q)))

;; check unnamed edge search
(expect (more-> 'a-node-0->b-node-0
                :symbol

                true
                :unnamed?)
  (in (:result (run* :unnamed-edge-complete [q] (edgeo q)))))

;; check node search
(expect (more-> #{"A" "B"} (->> :result (map :label) set))
  (run* :unnamed-edge-complete [q] (nodeo q)))

;; check distance extraction
(expect (more-> 'b-edge-0 :symbol
                'cm-0     (->> :unit :symbol)
                'num-5-0  (->> :length :symbol))
  (in (:result (run* :named-edge-distance [q] (edgeo q)))))

(expect
  (more-of {:keys [warnings result]}
    (more-> 'foo-0   (-> :unit :symbol)
            'num-5-0 (-> :length :symbol))
    (in result)

    #{:invalid-edge-length-unit}
    (set (map first warnings)))
  (from-each [db [:named-edge-distance-unit-invalid
                  :unnamed-edge-distance-unit-invalid]]
    (run* db [q] (edgeo q))))

(expect
  (more-of {:keys [warnings result]}
    (more-> 'cm-0      (-> :unit :symbol)
            'num-4.5-0 (-> :length :symbol))
    (in result)

    #{:invalid-edge-length-format}
    (set (map first warnings)))
  (from-each [db [:named-edge-distance-length-invalid
                  :unnamed-edge-distance-length-invalid]]
    (run* db [q] (edgeo q))))

(expect
  (more-of {:keys [warnings result]}
    (more-> 'foo-0     (-> :unit :symbol)
            'num-4.5-0 (-> :length :symbol))
    (in result)

    #{:invalid-edge-length-format
      :invalid-edge-length-unit}
    (set (map first warnings)))
  (from-each [db [:named-edge-distance-unit-length-invalid
                  :unnamed-edge-distance-unit-length-invalid]]
    (run* db [q] (edgeo q))))
