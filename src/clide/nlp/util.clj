(ns clide.nlp.util
  (:require [clojure.string :as str]))

(defn dissoc-index
  "Removes the element at index `i` from the vector `v`"
  [v i]
  (vec (concat (subvec v 0 i)
               (subvec v (inc i)))))

(defn copy-doc!
  "Copies doc string and arg list of var `orig-var` to var `new-var`."
  [orig-var new-var]
  (alter-meta! new-var
               (fn [new-meta]
                 (let [{:keys [doc arglists]} (meta orig-var)]
                   (assoc new-meta :doc doc :arglists arglists))))
  nil)
