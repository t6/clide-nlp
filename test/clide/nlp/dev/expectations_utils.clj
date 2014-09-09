(ns clide.nlp.dev.expectations-utils
  (:require [expectations :refer :all]
            [simple-check.core :as sc])
  (:import (java.io StringWriter)))

(defmacro expect-spec
  [n & body]
  `(expect {:result true}
     (in (sc/quick-check ~n ~@body))))
