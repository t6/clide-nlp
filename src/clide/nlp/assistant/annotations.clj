(ns clide.nlp.assistant.annotations
  "Utility functions for creating Clide annotations."
  (:refer-clojure :exclude [chunk])
  (:require [t6.snippets.span :as span]))

(defn chunk
  [{:keys [span] :as chunk} point & args]
  [[:plain (span 0)]
   [:annotate (span/length span)
    (reduce conj [] args)]
   [:plain (span 1)]])

(defn sentence
  [chunk point & args]
  (let [sentences  (-> chunk :annotations :sentences)
        [_ sentence] (span/span-at-point sentences point)]
    (when sentence
      (let [span (:span sentence)]
        [[:plain (span 0)]
         [:annotate (span/length span)
          (reduce conj [] args)]
         [:plain (span 1)]]))))

(defn span-annotations
  [offset span-annotations]
  (:annotations
   (reduce (fn [{:keys [offset annotations]} [span annotation]]
             (if span
               {:offset      (span 1)
                :annotations (conj annotations
                                   [:plain (- (span 0) offset)]
                                   [:annotate (span/length span)
                                    annotation])}
               {:offset      offset
                :annotations annotations}))
           {:offset      0
            :annotations []}
           span-annotations)))

(defn mark-with-tooltips
  [point class span-tooltips]
  (span-annotations point
                    (map (fn [[span tooltip]]
                           [span [[:Tooltip tooltip]
                                  [:Class (name class)]]])
                         span-tooltips)))

(defn mark
  [point class spans]
  (mark-with-tooltips point class (map (fn [x] [x ""]) spans)))
