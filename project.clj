(defproject t6/clide-nlp "0.1.0-SNAPSHOT"
  :description "An NLP Assistant for Clide"
  :url "https://github.com/t6/clide-nlp"
  :license {:name "GNU Lesser General Public License"
            :url "http://www.gnu.org/licenses/lgpl.html"}


  :pom-addition [:developers
                 [:developer
                  [:id "t6"]
                  [:name "Tobias Kortkamp"]
                  [:url "https://github.com/t6"]]]
  :scm {:name "git"
        :url "https://github.com/t6/clide-nlp"}

  :repositories [["sonatype" {:url    "https://oss.sonatype.org/content/groups/public/"
                              :update :always}]]

  :plugins [[lein-scalac "0.1.0"
             :exclusions [org.scala-lang/scala-compiler]]
            [org.scala-lang/scala-compiler "2.10.4"]
            [lein-expectations "0.0.8"]
            [codox "0.8.10"]]

  :source-paths ["src" "src-scala"]
  :scala-source-path "src-scala"
  :scalac-options {"deprecation" "yes"}

  :main clide.nlp.assistant.Main

  :jvm-opts ["-server"

             ;; Set initial heap space size to 2 GiB
             "-Xms2g"

             ;; Increase PermGen size
             "-XX:MaxPermSize=384m"

             "-XX:+UseConcMarkSweepGC"

             ;; Tell the JVM to try to minimize GC pause time.
             ;; Long GC pauses may cause clide-nlp to
             ;; dissociate from clide-core.
             "-XX:MaxGCPauseMillis=50"]

  :prep-tasks [;; Compile all Scala sources before running any
               ;; other command
               "scalac"]

  :repl-options {:init-ns clide.nlp.dev.system
                 :welcome (println "Welcome!\n\n"
                                   "(re-)start clide-nlp and a matching instance of clide-web in a separate JVM:\n"
                                   "  (clide.nlp.dev.system/restart!)\n\n"
                                   "stop clide-nlp and kill clide-web:\n"
                                   "  (clide.nlp.dev.system/stop!)\n")
                 :init    (do (require 'clojure.tools.namespace.repl)
                              (clojure.tools.namespace.repl/refresh))
                 :timeout 180000}

  :profiles {:light-table [:dev {:dependencies [[lein-light-nrepl "0.0.17"]]
                                 :repl-options {:nrepl-middleware [lighttable.nrepl.handler/lighttable-ops]}}]
             :dev {:dependencies [[expectations "2.0.9"]
                                  [org.clojure/tools.namespace "0.2.6"]
                                  [reiddraper/simple-check "0.5.6"]]}}

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.match "0.2.2"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]

                 [t6/snippets "0.1.0-SNAPSHOT"]
                 [t6/snippets-corenlp "0.1.0-SNAPSHOT"]
                 [t6/from-scala "0.1.0"]

                 [uk.org.russet/tawny-owl "1.1.0"]

                 #_[org.clojure/tools.logging "0.2.6"]
                 [ch.qos.logback/logback-classic "1.1.2"]
                 [org.slf4j/slf4j-log4j12 "1.7.7"]

                 [selmer "0.7.0"]
                 [instaparse "1.3.3"]

                 [com.stuartsierra/component "0.2.2"]

                 [com.typesafe.akka/akka-slf4j_2.10 "2.2.3"]
                 [org.scala-lang/scala-library "2.10.4"]

                 [seesaw "1.4.4"] ; for the starter
                 [org.apache.ant/ant "1.9.4"]
                 [t6/clide-web_2.10 "2.0-0b4b839d"
                  :exclusions [org.slf4j/slf4j-nop
                               ch.qos.logback/logback-classic
                               org.scala-lang/scala-library]]]

  :codox {:defaults {:doc/format :markdown}}

  :uberjar-merge-with {;; reference.conf may be included in multiple dependencies
                       ;; and needs to be merged for Akka to run. A simple
                       ;; concatenation seems to work...
                       ;; Also see http://doc.akka.io/docs/akka/snapshot/general/configuration.html
                       #"reference\.conf$" [slurp str spit]
                       #"application\.conf$" [slurp str spit]})
