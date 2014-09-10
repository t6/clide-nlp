;; Eventually you will be able to define triple queries here
;; Currently only reader errors are highlighted here (enable reader-errors)

(defquery noun-prep-noun
  {:examples [{:text "X is in the same state as Y"
               :triples '[[X is state]
                          [X :in state]]}]}
  (nsubj ?activity ?subj)
  (prep ?p ?activity ?obj)
  (l/pred ?obj noun?)
  (l/pred ?subj noun?)
  (l/pred ?activity verb?)
  (=> [?subj ?p ?obj]
      [?subj ?activity ?obj]))

(defquery #invalid)

