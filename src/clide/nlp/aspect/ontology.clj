(ns clide.nlp.aspect.ontology
  (:refer-clojure :exclude [chunk])
  (:require [tawny.owl :refer :all]
            [clojure.string :as str]))

(defn assert-object-property
  [prop c1 c2]
  (add-axiom (.getOWLObjectPropertyAssertionAxiom (owl-data-factory) prop c1 c2)))

(defn assert-data-property
  [prop c1 c2]
  (add-axiom (.getOWLDataPropertyAssertionAxiom (owl-data-factory) prop c1 c2)))

(defn add-word-group
  [class ind group]
  (let [token-prop      (datatype-property "hasToken")
        lemma-prop      (datatype-property "hasLemma")
        span-begin-prop (datatype-property "startsAtOffset")
        span-end-prop   (datatype-property "endsAtOffset")
        index-prop      (datatype-property "hasIndex")
        sentence-prop   (datatype-property "inSentence")]
    (doseq [word (:group group)]
      (assert-data-property token-prop ind (:token word))
      (assert-data-property lemma-prop ind (:lemma word))
      (assert-data-property index-prop ind (:index word))
      (assert-data-property sentence-prop ind (:sentence word))
      (assert-data-property span-begin-prop ind ((:span word) 0))
      (assert-data-property span-end-prop ind  ((:span word) 1)))))

(defn reified-triples->ontology
  "Export reified `triples` as an OWL ontology in RDF format."
  [triples filename]
  (with-ontology
    (ontology :iri "http://clide.informatik.uni-bremen.de/clide-nlp"
              :prefix "clide-nlp:")
    (doseq [{:keys [subject predicate object]} triples
            :let [subj-class (owl-class (name (:symbol subject)))
                  obj-class  (owl-class (name (:symbol object)))
                  subj-ind   (individual (name (:symbol subject)) :type subj-class)
                  obj-ind    (individual (name (:symbol object)) :type obj-class)]]
      (assert-object-property (object-property (name (:symbol predicate))) subj-ind obj-ind)
      (add-word-group subj-class subj-ind subject)
      (add-word-group obj-class obj-ind object))
    (save-ontology filename :rdf)))
