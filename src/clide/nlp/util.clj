(ns clide.nlp.util
  (:require [clojure.core.typed :as t :refer (All Any)]
            [clojure.string :as str]))

(defn dissoc-index
  "Removes the element at index `i` from the vector `v`"
  [v i]
  (vec (concat (subvec v 0 i)
               (subvec v (inc i)))))

(defn copy-doc!
  "Copies doc string and arg list of var `orig-var` to var `new-var`."
  [orig-var new-var]
  (t/tc-ignore
    (alter-meta! new-var
                 (fn [new-meta]
                   (let [{:keys [doc arglists]} (meta orig-var)]
                     (assoc new-meta :doc doc :arglists arglists)))))
  nil)

;;; ---------------------------------------------------------------------------
;;; Type annotations

(t/ann ^:no-check camel-snake-kebab/->kebab-case
       [String -> String])

(t/ann dissoc-index
       (All [x] [(t/Vec x) t/Int -> (t/Vec x)]))

(t/ann enum->keyword
       [(t/Option Enum) -> (t/Option t/Keyword)])

(t/ann copy-doc!
       [Any Any -> nil])

(t/ann parse-int
       [String t/Int -> (t/Option t/Int)])
