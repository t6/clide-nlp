(ns clide.nlp.aspect.draw
  "Triple queries that create a graph from a natural language specification."
  (:refer-clojure :exclude [== record?])
  (:require [clojure.string :as str]
            [clojure.java.shell :as sh]
            [clojure.set :as set]
            [clojure.core.logic.pldb :refer (with-db db-rel empty-db db-fact)]
            [clojure.core.logic :refer :all]
            [clide.nlp.logging :as log]
            [tobik.snippets.util :refer (parse-int)]
            [tobik.snippets.nlp :as nlp]
            [clide.nlp.knowledge-base.triples :refer (tripleo someo objecto-> subjecto->)]
            [clide.nlp.aspect.draw.warnings :as warnings]
            [plumbing.core :refer (defnk fnk)])
  (:import (java.util ArrayList)))

(def ^{:doc "A vector of words that should be recognized as units by the queries."}
  valid-units ["cm" "mm" "dm" "m"])

(def ^:dynamic
  ^{:doc "If bound to an atom, will be used by `add-warning` to collect
         warnings from inside core.logic queries."}
  *warnings*
  nil)

(defmacro add-warning
  "This macro creates a core.logic goal that always succeeds.
  It adds the warning specified by `type` to the global `*warnings*`
  atom. If `*warnings*` is not bound this will be essentially a no-op.
  `metadata` should be an object that supplements the warning.
  Note that `metadata` is projected using `clojure.core.logic/project`."
  [type metadata]
  `(let [var# ~metadata]
     (project [var#]
       (fn [a#]
         (when *warnings*
           (swap! *warnings* conj [~type var#]))
         a#))))

;;; --------------------------------------------------------------------------
;;; Queries for finding graph nodes
;;;
;;; Nodes are directly specified in the input text via sentence fragments like
;;;
;;;   "... Node <node-label> ..."
;;;

(defn node-lemma?
  [lemma]
  (= (str/lower-case lemma) "node"))

(defn node-labelo
  [node label]
  (fresh [group]
    (featurec node {:group group})
    (conda
      [(project [group]

                (let [l (->> group
                             (map :token)
                             distinct
                             (remove (comp #{"node"} str/lower-case))
                             first)]
                  (conda
                    [(conda
                       [(== l nil)]
                       [(== (str/lower-case l) "node")])
                     (add-warning :node-has-no-label node)
                     (== label "<unknown>")]
                    [(== l label)])))]
      [(add-warning :node-has-no-label node)
       (== label "<unknown>")])))

(defn nodeo
  [q]
  (fresh [t lemma group node label sym key]
    (nlp/triple t)

    (conde
     [(someo t :subject {:lemma lemma} (lvar))
      (featurec t {:subject {:group group :symbol sym}})]
     [(someo t :object {:lemma lemma} (lvar))
      (featurec t {:object {:group group :symbol sym}})])

    (pred lemma node-lemma?)
    (node-labelo {:group group} label)

    (== q {:group group :label label :symbol sym})))

;;; --------------------------------------------------------------------------
;;; Queries for finding graph edges
;;;
;;; We assume edges are undirected and distinguish between named and unnamed edges.
;;; Named edges are directly specified in the input text via sentences like
;;;
;;;   "Edge <edge-label> starts at node <node1-name> and goes to node <node2-name>."
;;;
;;; and unnamed edges via sentences like
;;;
;;;  "Node <node1-name> and node <node2-name> are connected."
;;;

(defn named-edgeo
  [q]
  (fresh [t from to symb subj w]
    (conda
      ;; try to find an edge and if that fails ...
      [(subjecto-> t [{:lemma "Edge"} :start from] [:at from] [:go to] [:to to])]
      ;; ... try to get more information
      [(fresh [t to from]
         (someo t :subject {:lemma "Edge"} (lvar))
         (condu
           [(subjecto-> t [:start from] [:at from])
            (conda
              [(subjecto-> t [:go to] [:to to])]
              [(add-warning :named-edge-has-no-end-node t)])]
           [(subjecto-> t [:go to] [:to to])
            (conda
              ;; [(subjecto-> t [:start from] [:at from])]
              [(add-warning :named-edge-has-no-start-node t)])])
         ;; fail this branch
         fail)])

    (featurec t {:subject subj})
    (featurec subj {:symbol symb})
    (== q {:from     from
           :to       to
           :symbol   symb
           ::subject subj
           :unnamed? false})))

(defn unnamed-edgeo
  [q]
  (fresh [t subj obj]
    (nlp/triple t)
    (featurec t {:subject   subj
                 :predicate :connect
                 :object    obj})
    (project [subj obj]
      (let [[subject object] (sort-by :symbol [subj obj])]
        (== q {:from     subject
               :to       object
               :symbol   (symbol (str (:symbol subject) "->" (:symbol object)))
               :unnamed? true})))))

;; ---------------------------------------------------------------------------
;; Queries for finding edge lengths
;;

(defn valid-unito
  [unit]
  (fresh [g u x]
    (featurec unit {:group g})
    (membero x g)
    (featurec x {:lemma u})
    (membero u valid-units)))

(defn valid-lengtho
  [length]
  (fresh [g u x]
    (featurec length {:group g})
    (membero x g)
    (featurec x {:lemma u})
    (pred u #(boolean (parse-int %1 10)))))

(defn valid-length-unito
  [edge unit length]
  (fresh []
    (conda
      [(valid-unito unit)]
      [(add-warning :invalid-edge-length-unit [edge unit])])

    (conda
      [(valid-lengtho length)]
      [(add-warning :invalid-edge-length-format [edge length])])))

(defn unnamed-edge-lengtho
  [edge unit length]
  (fresh [length-lemma from to from-triple to-triple]
    (featurec edge {:from from :to to})
    (featurec from-triple {:subject from})
    (featurec to-triple {:subject to})

    ;; both nodes must have the same object chain
    (condu
      [(everyg
         #(objecto-> %1
                     [:have (lvar) {:lemma "distance"}]
                     [:of unit]
                     [:be length {:tag :CD}])
         [from-triple to-triple])
       (conda
         [(valid-length-unito edge unit length)]
         ;; invalid unit and length, but we don't want another warning
         ;; about this valid-length-unito already added one
         [(== unit nil)
          (== length nil)])]
      ;; nothing found
      [(add-warning :no-edge-length-or-unit-found edge)
       (== unit nil)
       (== length nil)])))

(defn named-edge-lengtho
  [edge unit length]
  (fresh [symb length-lemma t subject]
    (featurec edge {:symbol symb})
    (featurec t {:subject {:symbol symb}})

    (conda
      [(objecto-> t
                  [:be (lvar) {:lemma "long"}]
                  [:be unit]
                  [:be length {:tag :CD}])

       (conda
         [(valid-length-unito edge unit length)]
         ;; invalid unit and length, but we don't want another warning
         ;; about this, valid-length-unito already added one
         [(== unit nil)
          (== length nil)])]
      ;; try finding length and unit by treating the edge as unnamed
      [(unnamed-edge-lengtho edge unit length)]
      ;; nothing found
      [(add-warning :no-edge-length-or-unit-found edge)
       (== unit nil)
       (== length nil)])))

;; ---------------------------------------------------------------------------
;; Queries for finding the label of a named edge

(defn named-edge-labelo
  [edge label]
  (fresh [group w lemma t subject object]
    (conda
      [(featurec edge {::subject {:group group}})
       (project [group]
         (let [l (->> group
                      (map :token)
                      distinct
                      (remove (comp #{"edge"} str/lower-case))
                      first)]
           (conda
             [(conda
                [(== l nil)]
                [(== (str/lower-case l) "edge")])
              (add-warning :named-edge-has-no-label edge)
              (== label "<unknown>")]
             [(== l label)])))]
      [(add-warning :named-edge-has-no-label edge)
       (== label "<unknown>")])))

;; ---------------------------------------------------------------------------
;;

(defn edgeo
  [q]
  (fresh [edge unit length label]
    (conde
      [(unnamed-edgeo edge)
       (unnamed-edge-lengtho edge unit length)
       (== label nil)]
      [(named-edgeo edge)
       (named-edge-lengtho edge unit length)
       (named-edge-labelo edge label)])

    (project [edge unit length label]
      (== q (assoc edge
              :unit unit
              :label label
              :length length)))))

;;; --------------------------------------------------------------------------
;;; Collectors that run the core.logic queries.
;;;
;;; For performance reasons, we do not use occurs checking here
;;; (`run-nc*` instead of `run*`).

(defn collect-edges
  [state]
  (binding [*warnings* (atom [])]
    (-> state
        ;; doall is important here, otherwise the *warnings* binding
        ;; might pop before run-nc* is realized
        (update-in [:edges] concat (distinct (doall (run-nc* [q] (edgeo q)))))
        (update-in [:warnings] concat @*warnings*))))

(defn collect-nodes
  [state]
  (binding [*warnings* (atom [])]
    (-> state
        ;; doall is important here, otherwise the *warnings* binding
        ;; might pop before run-nc* is realized
        (update-in [:nodes] concat (distinct (doall (run-nc* [q] (nodeo q)))))
        (update-in [:warnings] concat @*warnings*))))

;;; --------------------------------------------------------------------------
;;; Functions for creating the actual graph from the collected
;;; information and some additional consistency checks

(defn properties->dot
  [props]
  (when props
    (str "["
         (reduce (fn [acc [k v]]
                   (if (and k v)
                     (str acc (name k) "=\"" (str v) "\",")
                     acc)) "" props)
         "]")))

(defnk edge-properties
  [label length]
  {:label label
   :len   (if (= (count (:group length)) 1)
            (if-let [x (-> (:group length) first :lemma)]
              (parse-int x 10)))})

(defnk node-properties
  [label]
  {:label label})

(defnk ->dot
  [nodes edges :as state]
  (assoc state
    :dot (str "graph G {\ngraph[layout=neato];\n"
                 (str/join (map #(str (str/replace (name (get-in % [:from :symbol])) #"\-" "_")
                                      " -- "
                                      (str/replace (name (get-in % [:to :symbol])) #"\-" "_")
                                      " "
                                      (properties->dot (edge-properties %))
                                      ";\n") edges))
                 (str/join ";\n" (map #(str (str/replace (name (:symbol %)) #"\-" "_")
                                            " "
                                            (properties->dot (node-properties %)))
                                      nodes))

                 ";\n}")))

(defnk warn-about-duplicate-edges
  [edges :as state]
  (let [warnings (ArrayList.)]
    (-> state
        (assoc :edges (->> edges
                           (group-by :symbol)
                           (mapv (fn [[symbol group]]
                                   (if (> (count group) 1)
                                     (.add warnings [:edge-is-specified-multiple-times group]))
                                   ;; use the first edge of the group
                                   ;; even if there is more than one
                                   (first group)))))
        (update-in [:warnings] concat warnings))))

(defnk warn-about-unused-nodes
  [edges nodes :as state]
  (let [unused-nodes (->> nodes
                          (filter (comp (set/difference
                                         (set (map :symbol nodes))
                                         (set (map :symbol (mapcat (juxt :from :to) edges))))
                                        :symbol))
                          (sort-by :symbol)
                          distinct)]
    (if (seq unused-nodes)
      (update-in state [:warnings]
        conj
        [:unused-nodes unused-nodes])
      state)))

(defnk reify-warnings
  [warnings :as state]
  (assoc state
    :warnings (distinct
               (for [[type _ :as warning] warnings]
                 {:word-maps   (sort-by (juxt :sentence :index)
                                        (warnings/word-maps warning))
                  :suggestions (warnings/suggestions warning)
                  :description (warnings/description warning)
                  :type        type}))))

(defn extract-graph
  [db]
  (with-db db
    (-> {}
        collect-nodes
        collect-edges
        warn-about-duplicate-edges
        warn-about-unused-nodes
        (update-in [:warnings] (comp (partial sort-by first)
                                     distinct))
        reify-warnings
        ->dot)))

(defn triples->dot
  [db reified-triples]
  (extract-graph db))
