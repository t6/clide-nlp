(ns clide.nlp.assistant.core
  (:require [clide.nlp.assistant.reconciler :as reconciler]
            [t6.from-scala.core :refer ($) :as $]
            [clojure.java.shell :as sh]
            [clojure.core.async :as async]
            [clojure.set :as set]
            [clojure.string :as str]
            [clide.nlp.logging :as log]            
            [clojure.core.match :refer (match)]
            [clojure.core.logic :as l]
            [t6.snippets.span :as span]
            [t6.snippets.nlp :as nlp]
            [t6.snippets.nlp.viz :as viz]
            [clide.nlp.assistant.annotations :as annotate]
            [plumbing.core :refer (for-map)]
            [lazymap.core :refer (lazy-hash-map delayed-hash-map)]
            [schema.core :as s]
            [selmer.parser :refer (render-file)])
  (:import (java.io IOException)
           (clide.nlp.assistant Utilities)
           (clide.collaboration AnnotationType)))

;;; ---------------------------------------------------------------------------
;;; Annotators

(defn ^::global
  chunk-separators
  "highlight all reconciler chunk separators"
  [state chunk point]
  (->> (:chunks state)
       (filter :separator?)
       (map :span)
       (annotate/mark point "info")))

(defn linked
  [state chunk point]
  (let [db     (-> chunk :annotations)
        corefs (nlp/with-db db
                 (l/run* [q]
                   (l/fresh [iw span group]
                     ;; match word at point ...
                     (nlp/word-map iw)
                     (l/featurec iw {:span span})
                     (l/pred span #(span/point-inside? %1 point))
                     
                     ;; ... and "return all" coreferences of `iw`
                     (nlp/linked iw q))))]
    (annotate/mark point "error"
                   (map :span (sort-by (juxt :sentence :index) corefs)))))

(defn reified-name
  [state chunk point]
  (let [db     (-> chunk :annotations)
        corefs  (nlp/with-db db
                  (l/run* [q]
                    (l/fresh [iw t subj-group obj-group span subj-sym obj-sym]
                      ;; match word at point ...
                      (nlp/word-map iw)
                      (l/featurec iw {:span span})
                      (l/pred span #(span/point-inside? %1 point))
                      
                      ;; find a reified triple which contains our word map
                      (nlp/triple t)
                      (l/featurec t {:subject {:symbol subj-sym
                                               :group  subj-group}
                                     :object  {:symbol obj-sym
                                               :group  obj-group}})
                      (l/conde
                       [(l/membero iw subj-group)
                        (l/== q {subj-sym subj-group})]
                       [(l/membero iw obj-group)
                        (l/== q {obj-sym obj-group})]))))
        corefs (reduce (partial merge-with #(set/union (set %1) (set %2))) {} corefs)]
    (when (= 1 (count (keys corefs)))
      (let [[sym words] (first corefs)]
        (annotate/mark-with-tooltips
         point "error"
         (map (fn [{:keys [span]}]
                [span (name sym)])
              (sort-by (juxt :sentence :index) words)))))))

(defn triple-table-template
  [fmt triples]
  (render-file "clide/nlp/assistant/core/triples.html"
               {:triples (mapv fmt triples)}))

(defn grouped-triples
  [state chunk point]
  (let [triples (-> chunk :annotations :grouped-triples)
        span    (:span chunk)
        fmt (fn [{:keys [subject-group predicate object-group] :as t}]
              {:subject (print-str (mapv nlp/word-map->text subject-group))
               :predicate (if (keyword? predicate)
                            predicate
                            (nlp/word-map->text predicate))
               :object (print-str (mapv nlp/word-map->text object-group))
               :query (get-in (meta t) [:origin :query])})]
    (when triples
      (annotate/chunk chunk point
                      [:Output (triple-table-template fmt triples)]))))

(defn triples
  [state chunk point]
  (let [triples (-> chunk :annotations :triples)
        span    (:span chunk)
        fmt (fn [{:keys [subject predicate object query]}]
              {:subject (nlp/word-map->text subject)
               :predicate (if (keyword? predicate)
                            predicate
                            (nlp/word-map->text predicate))
               :object (nlp/word-map->text object)
               :query query})]
    (when triples
      (annotate/chunk chunk point
                      [:Output (triple-table-template fmt triples)]))))

(defn reified-triples
  [state chunk point]
  (let [triples (-> chunk :annotations :reified-triples)
        span    (:span chunk)
        fmt     (fn [{:keys [subject predicate object] :as t}]
                  {:subject (:symbol subject)
                   :predicate predicate
                   :object (:symbol object)
                   :query (get-in (meta t) [:origin :query])})]
    (when triples
      (annotate/chunk chunk point
                      [:Output (->> triples
                                    (sort-by (juxt :predicate
                                                   (comp :symbol :subject)))
                                    (triple-table-template fmt))]))))

(defn wrap-dot
  [dot]
  ;; wrap dot file in a div, clide-web takes care of actually
  ;; rendering it
  (format "<div style='overflow: auto;' ><script type=\"text/vnd.graphviz\">%s</script></div>"
          dot))

(defn stream->html
  [stream]
  (->> stream
       (map (fn [tag]
              (match [tag]
                [[:edge s]]
                (format "<code>%s</code>" s)

                [[:node s]]
                (format "<code>%s</code>" s)

                [[:quote s]]
                (format "<code>%s</code>" s)

                [[type s]]
                (do
                  (log/warn "stream->html: unhandled tag type" type)
                  s)

                [(s :guard string?)]
                s

                :else
                (do
                  (log/warn "stream->html: ignoring tag" tag)
                  ""))))
       (str/join " ")))

(defn draw
  [state chunk point]
  (let [{:keys [dot warnings]} (-> chunk :annotations :draw)
        sentences              (-> chunk :annotations :sentences)]
    {:warning-highlights (annotate/mark
                          point
                          :warning
                          (->> warnings
                               (mapcat (fn [{:keys [word-maps type suggestions]}]
                                         (distinct (sort (map :span word-maps)))))
                               sort))
     :warnings           (annotate/chunk
                          chunk
                          point
                          [:WarningMessage
                           (render-file
                            "clide/nlp/assistant/core/draw.html"
                            {:warnings
                             (map (fn [{:keys [word-maps suggestions description] :as w}]
                                    {:suggestions (map stream->html suggestions)
                                     :sentences   (for [[sentence words]
                                                        (sort (group-by :sentence (sort-by :sentence (distinct word-maps))))
                                                        :when sentence]
                                                    {:index sentence
                                                     :text (.trim (:text (nth sentences sentence)))
                                                     :words words})
                                     :text        (stream->html description)})
                                  warnings)})])
     :default            (annotate/chunk chunk point [:Output (wrap-dot dot)])}))

(defn semantic-graph
  [state chunk offset]
  (let [graphs (-> chunk :annotations :semantic-graphs)
        [_ graph] (span/span-at-point graphs offset)]
    (when graph
      (annotate/sentence chunk offset
                         [:Output
                          (wrap-dot (viz/semantic-graph->dot graph))]
                         [:Class "info"]))))

(defn coref-chain-cluster->dot
  [mention-maps]
  (let [{:keys [nodes edges]}
        (reduce (partial merge-with clojure.set/union)
                (for [m1 mention-maps
                      m2 mention-maps
                      :when (not= m1 m2)]
                  {:nodes #{m1 m2}
                   :edges #{#{m1 m2}}}))

        nodes->id (into {} (for [node nodes] {node (str (gensym "node"))}))]
    (with-out-str
      (doseq [node nodes]
        (println (str (pr-str (nodes->id node)) "[shape=box,label="
                      (pr-str (format "%s [%s:%s-%s]"
                                      (:text node)
                                      (:sentence node)
                                      ((:span node) 0)
                                      ((:span node) 1))) "];")))
      (doseq [nodes edges
              :let [[n1 n2] (vec nodes)]]
        (println (str (pr-str (nodes->id n1)) "--" (pr-str (nodes->id n2)) ";"))))))

(defn coref-chain-map->dot
  [coref-chain-map]
  (with-out-str
    (println "graph G {")
    (doseq [[_ mention-maps] coref-chain-map]
      (print (coref-chain-cluster->dot mention-maps)))
    (println "}")))

(defn coref-clusters
  [state chunk offset]
  (let [coref-chain-map (-> chunk :annotations :coreferences)]
    (when coref-chain-map
      (annotate/chunk chunk offset
                      [:Output (wrap-dot (coref-chain-map->dot coref-chain-map))]))))

(defn ontology
  [state chunk offset]
  (let [ontology (-> chunk :annotations :ontology)]
    (annotate/chunk chunk offset
                    [:Output
                     (render-file "clide/nlp/assistant/core/ontology.html"
                                  {:ontology ontology
                                   :filename "ontology.rdf"})])))

(defn eval-annotation
  [state chunk offset]
  {:default []})

(defn ^::global
  reader-errors
  [state chunk offset]
  (if-let [errors (-> chunk :annotations :reader-errors)]
    (when-not (empty? errors)
      (annotate/span-annotations 0
                                 (map (fn [{:keys [span message]}]
                                        [span [[:ErrorMessage message]
                                               [:Tooltip message]
                                               [:Class "error"]]])
                                      errors)))))

;;; ---------------------------------------------------------------------------
;;; Assistant

(def AnnotationStreamsSchema
  {s/Keyword {(s/required-key :annotator)  clojure.lang.Var
              (s/required-key :streams)    {s/Keyword s/Str}
              (s/required-key :mime-types) (s/both #{String}
                                                   (s/pred #(>= (count %1) 1)
                                                           'at-least-one-element))}})

(def annotation-streams
  "Describes all annotation streams that clide-nlp can offer."
  ;; Use var references here if you want to change the annotation functions
  ;; in the REPL and have the changes reflect immediately in Clide without
  ;; restarting the assistant.
  (s/validate
    AnnotationStreamsSchema
    {:eval             {:annotator  #'eval-annotation
                        :streams    {:default       "Triple query eval"}
                        :mime-types #{"text/x-clojure"}}
     :reader-errors    {:annotator  #'reader-errors
                        :streams    {:default "Reader errors"}
                        :mime-types #{"text/x-clojure"}}
     :semantic-graph   {:annotator  #'semantic-graph
                        :streams    {:default "Semantic graph"}
                        :mime-types #{"text/plain"}}
     :chunk-separators {:annotator  #'chunk-separators
                        :streams    {:default "Chunk separators"}
                        :mime-types #{"text/plain" "text/x-clojure"}}
     :coref-clusters   {:annotator  #'coref-clusters
                        :streams    {:default "Coreference clusters"}
                        :mime-types #{"text/plain"}}
     :linked           {:annotator  #'linked
                        :streams    {:default "Linked"}
                        :mime-types #{"text/plain"}}
     :draw             {:annotator  #'draw
                        :streams    {:default            "Extracted graph"
                                     :warning-highlights "Highlight graph warnings"
                                     :warnings           "Show graph warnings"}
                        :mime-types #{"text/plain"}}
     :grouped-triples  {:annotator  #'grouped-triples
                        :streams    {:default "Grouped triples"}
                        :mime-types #{"text/plain"}}
     :reified-triples  {:annotator  #'reified-triples
                        :streams    {:default "Reified triples"}
                        :mime-types #{"text/plain"}}
     :reified-name     {:annotator  #'reified-name
                        :streams    {:default "Reified symbol tooltips for words"}
                        :mime-types #{"text/plain"}}
     :triples          {:annotator  #'triples
                        :streams    {:default "Triples"}
                        :mime-types #{"text/plain"}}
     :ontology         {:annotator  #'ontology
                        :streams    {:default "Ontology"}
                        :mime-types #{"text/plain"}}}))

(defn annotation-stream-descriptions
  "Generate a map in the form of `{\"<stream-name>\" \"<stream description>\"}` from
  an annotation stream description."
  []
  (into {}
    (mapcat (fn [[ann-name {f :annotator, submap :streams}]]
              (map (fn [[k description]]
                     {(if (= k :default)
                        (name ann-name)
                        (str (name ann-name) "-" (name k)))
                      description})
                   submap))
            annotation-streams)))

(defn transform-annotation-spans
  [span annotations]
  (let [annotations (if (vector? annotations)
                      {:default annotations}
                      annotations)]
    (for-map [[k annotation] annotations]
      k
      (match [annotation]
        ;; translate the first annotation (a :plain annotation) into
        ;; global span coordinates. All other annotations in the stream
        ;; are relative to the preceding ones.
        [[[:plain x] & rest]]
        (cons [:plain (nth (span/project span [x x]) 0)] rest)

        [[]]
        []

        [nil]
        []

        :else
        (throw (IllegalArgumentException.
                (format "mismatching annotation stream: %s"
                        (pr-str annotations))))))))

(defn annotate
  [state chunk anchor]
  (let [span        (:span chunk)
        point       (span/project-point span anchor)
        transform   (partial transform-annotation-spans span)
        ;; update chunk's span to use local coordinates (matching the spans of
        ;; the chunks' CoreNLP annotations for easier coordination.
        ;;
        ;; The annotation functions then do not have to worry about translating
        ;; between chunk local and global editor spans.
        ;;
        ;; Annotation functions can be tagged with ^::global if they do not
        ;; want this behavior.
        ;;
        ;; The annotations themselves will be transformed afterwards.
        local-chunk (assoc chunk :span [0 (- (nth span 1) (nth span 0))])]
    (apply delayed-hash-map
           (mapcat (fn [[stream {f :annotator}]]
                     (if (::global (meta f))
                       [stream (delay (f state chunk point))]
                       [stream (delay (transform (f state local-chunk point)))]))
                annotation-streams))))

(defn annotation-loop
  [in-chan]
  (let [annotation-chan (async/chan)]
    ;; Receives tuples of `[file state chunk anchor]` from `in-chan` and sends
    ;; a lazy map of all possible annotations for the chunk at `anchor` to `annotation-chan`.
    (async/go-loop []
      ;; We receive a Scala tuple from the channel, which we need to unpack
      (when-let [[file state chunk anchor] ($/view (async/<! in-chan))]
        (try
          (if (and chunk (:annotate? chunk))
            (if-let [annotation (annotate state chunk anchor)]
              (async/>! annotation-chan [file state chunk annotation])
              (async/>! annotation-chan [file state chunk {}]))
            (async/>! annotation-chan [file state chunk {}]))
          (catch Throwable t
            (log/warn t "")))
        (recur)))
    annotation-chan))

(defn assistant-loop-helper
  [control file state chunk annotations]
  (doseq [[k {:keys [streams mime-types]}] annotation-streams
          :when (mime-types ($/view (.. file info mimeType)))
          ann (keys streams)
          :let [ann-name (if (= :default ann)
                           (name k)
                           (str (name k) "-" (name ann)))]
          :when ($ control isSubscribed file ann-name)
          :let [annotation (try
                             (k annotations)
                             (catch Throwable t
                               (.printStackTrace t)
                               (log/warn t "Failed to realize annotation value for key" k)
                               nil))]]
    ($ control annotate file ann-name
       ($ Utilities/dataToAnnotations
          (if (map? annotation)
            (annotation ann)
            annotation))
       ;; use default delay
       _)))

(defn throttle-channel
  "Returns a channel that wraps `input-channel` and for `time` ms
  discards every element read from that channel.
  After `time` ms puts the last read element on the return channel."
  [input-channel time]
  (let [output-channel (async/chan (async/sliding-buffer 1))]
    (async/go-loop [x nil]
      (async/alt!
       input-channel
       ([x] (recur x))

       (async/timeout time)
       (do
         (if x
           (async/>! output-channel x))
         (recur nil))

       :priority true))
    output-channel))

(defn assistant-loop
  [control in-chan time]
  (let [annotation-chan (throttle-channel (annotation-loop in-chan) time)]
    (log/info "assistant-loop started")
    ;; Receives lazy maps of annotations from `annotation-chan` and
    ;; sends them to clide.
    (async/go-loop []
      (when-let [[file state chunk annotations] (async/<! annotation-chan)]
        (try
          ($ control workOnFile file)
          (#'assistant-loop-helper control file state chunk annotations)
          (catch Throwable t
            (let [message (format "Failed in file %s: %s" (.. file info id) (pr-str t))]
              ($ control chat message ($/option nil))
              (log/warn t message))))
        ($ control doneWithFile file)
        (recur)))))
