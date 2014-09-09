(ns clide.nlp.dev.expectations-utils
  (:require [clojure.core.typed :as t]
            [expectations :refer :all]
            [simple-check.core :as sc])
  (:import (java.io StringWriter)))

;; Set this to `false` to globally disable all type checking in tests
(def type-checking-enabled? true)

(defn type-check-ns
  "Type checks `namespace` like `clojure.core.typed/check-ns` but
   does it silently i.e. it only outputs the type checking log if the
   namespace will not type check!"
  [namespaces]
  (if type-checking-enabled?
    (let [writer (StringWriter.)
          result (binding [*out* writer]
                   (t/check-ns namespaces))]
      (when-not result
        (println (str writer)))
      result)
    :ok))

(defmacro expect-spec
  [n & body]
  `(expect {:result true}
     (in (sc/quick-check ~n ~@body))))
