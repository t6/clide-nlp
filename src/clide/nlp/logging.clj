(ns clide.nlp.logging
  "Macros that wrap the logging macros in clojure.tools.logging with
   core.typed's tc-ignore."
  #_(:require #_[clojure.tools.logging :as log]))

(defmacro log-capture!
  [& args]
  `(do) #_(log/log-capture! ~@args))

(defmacro info
  [& args]
  `(println "INFO:" ~@args) #_(log/info ~@args))

(defmacro warn
  [& args]
  `(println "WARN:" ~@args) #_(log/warn ~@args))

(defmacro debug
  [& args]
  `(println "DEBUG:" ~@args) #_(log/debug ~@args))

;; Copy the docstrings of the clojure.tools.logging macros
;; to our own macros.
(comment
  (copy-doc! #'log/log-capture! #'log-capture!)
  (copy-doc! #'log/info #'info)
  (copy-doc! #'log/warn #'warn)
  (copy-doc! #'log/debug #'debug))
