(ns clide.nlp.logging
  "Macros that wrap the logging macros in clojure.tools.logging with
   core.typed's tc-ignore."
  (:require #_[clojure.tools.logging :as log]
            [clojure.core.typed :as t]))

(defmacro log-capture!
  [& args]
  `(t/tc-ignore #_(log/log-capture! ~@args)))

(defmacro info
  [& args]
  `(t/tc-ignore (println "INFO:" ~@args) #_(log/info ~@args)))

(defmacro warn
  [& args]
  `(t/tc-ignore (println "WARN:" ~@args) #_(log/warn ~@args)))

(defmacro debug
  [& args]
  `(t/tc-ignore (println "DEBUG:" ~@args) #_(log/debug ~@args)))

;; Copy the docstrings of the clojure.tools.logging macros
;; to our own macros.
#_(t/tc-ignore
  (copy-doc! #'log/log-capture! #'log-capture!)
  (copy-doc! #'log/info #'info)
  (copy-doc! #'log/warn #'warn)
  (copy-doc! #'log/debug #'debug))
