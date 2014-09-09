(ns clide.nlp.knowledge-base.triples
  (:refer-clojure :exclude [== record?])
  (:require [clojure.core.logic.pldb :refer (db-fact db-rel)]
            [clojure.core.typed :as t]
            [clojure.core.logic :refer :all]
            [tobik.snippets.nlp :refer (ReifiedTriple triple)]
            [clide.nlp.knowledge-base.types :refer (KnowledgeBase Relation Goal)]))

(declare subjecto-helper objecto-helper)

(defn someo
  "Matches `pmap` on each word map in the triple `t`.
  Use `key` to select if you want to match on its subject
  group (`:subject`) or its object group (`:object`).
  `word` is unified with the word map that matched `pmap`."
  [t key pmap word]
  (fresh [group]
    (triple t)
    (featurec t {key {:group group}})
    (membero word group)
    (featurec word pmap)))

(defn tripleo
  [[subj pred obj] t]
  (fresh []
    (triple t)
    (featurec t {:subject   subj
                 :predicate pred
                 :object    obj})))

(defnu subjecto-helper
  [q init-triple init-subject chain]
  ([_ _ _ []]
   (== q init-triple))
  ([_ _ _ [[predicate object] . tail]]
   (fresh [t]
     (triple t)
     (featurec init-triple {:predicate predicate
                            :object    object
                            :subject   init-subject})
     (subjecto-helper q t init-subject tail)))
  ([_ _ _ [[pmap predicate object] . tail]]
   (fresh [t]
     (triple t)
     (featurec init-triple {:predicate predicate
                            :object    object
                            :subject   init-subject})
     (someo init-triple :subject pmap (lvar))
     (subjecto-helper q t init-subject tail))))

(defn subjecto->
  [t & chain]
  (fresh [subject]
    (triple t)
    (featurec t {:subject subject})
    (subjecto-helper t t subject chain)))

(defnu objecto-helper
  [q init-triple init-subject chain]
  ([_ _ _ []]
   (== q init-triple))
  ([_ _ _ [[predicate object] . tail]]
   (fresh [t]
     (triple t)
     (featurec init-triple {:predicate predicate
                            :object    object
                            :subject   init-subject})
     (objecto-helper q t object tail)))
  ([_ _ _ [[predicate object pmap] . tail]]
   (fresh [t]
     (triple t)
     (featurec init-triple {:predicate predicate
                            :object    object
                            :subject   init-subject})
     (someo init-triple :object pmap (lvar))
     (objecto-helper q t object tail))))

(defn objecto->
  "The 'object thread' goal `objecto->` follows a chain of triples.

      (run* [q]
        (objecto-> q {:symbol 'B-Edge-it-0}
                     [:be {:lemma \"long\"}]
                     [:be {:lemma \"cm\"}]))

  will follow the triples

      [B-Edge-it-0 :be long-1]
      [long-1      :be cm-3]

  and will return the word maps with lemmas 'long' and 'cm' that are
  inside the triples object groups.

  Will succeed iff all rules succeed."
  [t & chain]
  (fresh [subject]
    (triple t)
    (featurec t {:subject subject})
    (objecto-helper t t subject chain)))

