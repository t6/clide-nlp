(ns clide.nlp.assistant.reconciler
  "The reconciler splits text files into chunks and creates a computation model
  for each chunk. It provides support for Clide operations, so that the chunks
  are kept up to date and in sync with Clide's internal state."
  (:require [clojure.string :as str]
            [clojure.core.match :refer (match)]
            [clojure.core.typed :as t :refer (Any)]
            [clojure.tools.reader :as r]
            [clojure.tools.reader.reader-types :as rt]
            [clide.nlp.logging :as log]
            [tobik.snippets.span :as span :refer (Span)]
            [tobik.snippets.core :as model]
            [tobik.snippets.nlp :as nlp]
            tobik.snippets.nlp.corenlp
            tobik.snippets.triples
            [lazymap.core :refer (lazy-hash-map)])
  (:import (clojure.lang ExceptionInfo)))

;;; ---------------------------------------------------------------------------
;;; Reconciler

(declare reconcile-chunks)

(defmulti chunks
  "Splits the input text into chunks. This is a multi-method that
  dispatches on the text's MIME type. It should return a vector of chunks.
  Check the type alias `Chunk` for what a chunk has to look like."
  (fn [mime-type text]
    mime-type))

(defn initialize
  "Prepares `text` for annotation. This intializes the reconciler and
  returns an initial state. See `State`."
  ([text]
   (initialize "text/plain" text))
  ([mime-type text]
   (let [text-chunks (chunks mime-type text)]
     (reconcile-chunks
       {:text      (or text "")
        :pipeline  (nlp/pipeline {:type :corenlp})
        :mime-type mime-type
        :chunks    (vec text-chunks)}))))

(defn- dash-paragraph-chunker-iter
  [[i start chunks] chunk]
  [(inc i)
   (+ start (count chunk))
   (conj chunks
         {:text        chunk
          :index       i
          :annotations nil
          :reconcile?  true
          :separator?  (= "\n----\n" chunk)
          :annotate?   (not= "\n----\n" chunk)
          :span        [start (+ start (count chunk))]})])

(defn dash-paragraph-chunker
  "Extracts all chunks from the string `text`"
  [text]
  ;; TODO
  (nth (->> (str/split text #"\n----\n")
            (interpose "\n----\n")
            (reduce dash-paragraph-chunker-iter [0 0 []]))
       2))

(defmethod chunks "text/plain"
  [_ text]
  (vec (dash-paragraph-chunker text)))

(defmethod chunks "text/x-clojure"
  [mime-type text]
  (let [lines
        (loop [rdr (rt/indexing-push-back-reader text)
               ;; the extra nil removes the need to decrement the line number
               ;; in the loop below, when looking up the span of a line
               lines (transient [nil])
               prev-line-number 1
               offset 0
               stop-reading? false]
          (if stop-reading?
            (persistent! lines)
            (let [line (rt/read-line rdr)
                  line-number (rt/get-line-number rdr)]
              (recur rdr
                     (conj! lines [offset (+ offset (count line) 1)])
                     line-number
                     (+ offset (count line) 1)
                     ;; stop reading if the line number did not advance
                     (= line-number prev-line-number)))))
        warnings (atom [])]
    (letfn [(add-chunk
              ([chunks span separator?]
               (conj! chunks
                      {:text        (span/subs text span)
                       :index       (count chunks)
                       :span        span
                       :annotations (lazy-hash-map
                                      :reader-errors @warnings)
                       :separator?  separator?
                       :annotate?   true
                       :reconcile?  (not separator?)}))
              ([chunks span]
               (add-chunk chunks span false)))

            (add-separator
              [chunks prev-span next-span]
              (if-let [span (span/difference prev-span next-span)]
                (add-chunk chunks span true)
                chunks))

            (read-form
              [rdr]
              (let [form
                    (try
                      (r/read rdr false ::end)
                      (catch ExceptionInfo e
                        ;; skip a char until we can read a form again
                        ;; or until we reach EOF
                        ;; this allows us to chunk an invalid clojure file
                        (swap! warnings conj
                               (assoc (ex-data e)
                                 :span (span/update (nth lines (:line (ex-data e)))
                                                    ;; ignore newline
                                                    [0 -1])
                                 :message (.getMessage e)))
                        (if (rt/read-char rdr)
                          ::recur
                          ::end)))]
                (if (= form ::recur)
                  (recur rdr)
                  form)))]
      (if (empty? text)
        ;; special case empty string, not a separator
        (persistent! (add-chunk (transient []) [0 0]))
        (loop [rdr (rt/indexing-push-back-reader text)
               chunks (transient [])
               last-span [0 0]]
          (let [form (read-form rdr)
                {:keys [end-line end-column line column]} (meta form)]
            (if (= form ::end)
              (persistent!
                ;; check if there is more text at the end of the file
                (add-separator chunks last-span [(count text) (count text)]))
              (let [start (+ (nth (nth lines line) 0) column -1)
                    end (+ (nth (nth lines end-line) 0) end-column -1)
                    span [start end]]
                (recur rdr
                       (-> chunks
                           (add-separator last-span span)
                           (add-chunk span))
                       span)))))))))

(defmethod chunks :default
  [mime-type _]
  (log/warn "No chunker implementation for MIME type" mime-type))

(defmulti annotate
  ""
  (fn [state chunk] (:mime-type state)))

(defmethod annotate :default
  [state chunk]
  (log/warn "Missing clide.nlp.assistant.reconciler/annotate implementation for mime type"
            (:mime-type state)))

(defmethod annotate "text/plain"
  [state chunk]
  (model/create (assoc chunk
                  :queries  (model/queries-from-namespace 'tobik.snippets.triples)
                  :pipeline (:pipeline state))))

(defmethod annotate "text/x-clojure"
  [state chunk])

(defn- apply-delta-helper
  [[rest-of-text result] op]
  (match [op]
    [[:retain n]]
    (cond
      (< n 0)
      (do
        (log/warn "Ignoring negative retain" n "and assuming end of text")
        ["" result])

      (> n (count rest-of-text))
      (do
        (log/warn (format "Ignoring excessive retain %s (max %s)"
                          n
                          (count rest-of-text)))
        ["" (conj result (subs rest-of-text 0 (count rest-of-text)))])

      :else
      [(subs rest-of-text n)
       (conj result (span/subs rest-of-text [0 n]))])

    [[:insert s]]
    [rest-of-text
     (conj result s)]

    [[:delete n]]
    (cond
      (< n 0)
      (do
        (log/warn "Ignoring negative delete" n)
        [rest-of-text result])

      (> n (count rest-of-text))
      (do
        (log/warn (format "Interpreting excessive delete %s (max %s) as deleting until end of text."
                          n
                          (count rest-of-text)))
        ["" result])

      :else
      [(subs rest-of-text n)
       result])

    :else (throw (IllegalArgumentException.
                  (format "unknown delta operation %s"
                          (pr-str op))))))

(defn apply-delta
  "applies the edit operation list `delta` to `text` and returns
   an updated version of `text`."
  [text delta]
  (str/join (second (reduce apply-delta-helper [text []] delta))))

;; --------------------------------------------------------------------
;; update functions

(defn- subsequent-span-update-iter
  [dspan spans i]
  (update-in spans [i] span/update dspan))

(defn- subsequent-span-update
  [spans base dspan]
  (let [start (inc base)]
    (if-let [end-index (dec (count spans))]
      (if (> start end-index)
        spans
        (reduce (partial subsequent-span-update-iter dspan)
                spans
                (range start (inc end-index)))))))

(defn- apply-delta-update-state
  [spans offset i span [op x]]
  (let [delta (if (= op :insert)
                (count x)
                (- x))]
    (-> (subsequent-span-update-iter [0 delta] spans i)
        (subsequent-span-update i [delta delta]))))

(defn- apply-delta-to-chunks-iter
  ""
  [[updated-spans spans offset] op]
  (match [op]
    [[:retain n]]
    (if (< n 0)
      (do
        (log/warn "Ignoring negative retain" n)
        [updated-spans spans offset])
      [updated-spans
       spans
       (+ offset n)])

    [(:or [:insert _] [:delete _])]
    [(if-let [[i span] (span/span-at-point spans offset)]
       (apply-delta-update-state updated-spans offset i span op)

       (do
         (log/debug
           (format "no span found for op: %s offset: %s, assuming operation applies to end of string"
                   op
                   offset))
         (if-let [span (last spans)]
           (apply-delta-update-state updated-spans offset (dec (count spans)) span op)
           (throw (IllegalStateException.
                    (format "no last span? op: %s offset: %s"
                            op
                            offset))))))
     spans
     offset]

    :else
    (throw (IllegalArgumentException.
            (format "unknown delta operation %s"
                    (pr-str op))))))

(defn- mark-chunks-for-reconciling
  [state index [span & spans]]
  (if span
    (let [old-span (get-in state [:chunks index :span])]
      (recur
       ;; check if the chunk's span changed, if it did
       ;; the chunk needs to be reconciled again
       (if (= span old-span)
         state
         (update-in state [:chunks index] assoc
           :span        span
           :reconcile?  true))
       (inc index)
       spans))
    state))

(defn- apply-delta-to-chunks
  "Updates the span of all chunks in `state` as appropriate for the
   edit operations in `delta`.

   This function updates the chunks' :span values, use them
   later to update all of the other values as needed.

   If a chunk's span changed its annotations will be reset as well.
   Any previous annotations will be lost."
  [state delta]
  (let [spans (vec (map :span (:chunks state)))
        spans (first (reduce apply-delta-to-chunks-iter [spans spans 0] delta))]
    (mark-chunks-for-reconciling state 0 spans)))

(defn- update-chunk
  "See `update-chunks`."
  [state chunk]
  (let [span (:span chunk)
        text (span/subs (:text state) span)]
    (assoc-in state [:chunks (:index chunk) :text] text)))

(defn- update-chunks
  "Updates the associated text for all chunks by re-extracting the text
   from `state` based on each chunk's :span value."
  [state]
  (reduce update-chunk state (:chunks state)))

(defn detect-new-chunks
  "Chunk the text again and see if the chunk count matches
  the chunk previous chunk count, if not reinitalize the
  reconciler (this likely means someone added a new chunk!)."
  [state]
  (let [fresh-state (initialize (:mime-type state) (:text state))]
    (if (or (not= (count (:chunks fresh-state))
                  (count (:chunks state)))
            (not= (mapv :separator? (:chunks fresh-state))
                  (mapv :separator? (:chunks state))))
      (do
        (log/info "chunk layout changed. reinitializing reconciler...")
        fresh-state)
      state)))

(defn reconcile-chunk
  "Reconciles `chunk` if it is marked for reconiliation.
  Returns an updated version of `state` that will contain
  an updated version of the chunk with `:annotations` set."
  [state chunk]
  (if (:reconcile? chunk)
    (if-let [index (:index chunk)]
      (update-in state [:chunks index]
        assoc
        :annotations (merge
                       ;; preserve annotations that were added at
                       ;; chunk creation time
                       (:annotations chunk)
                       (if-not (:separator? chunk)
                         (annotate state chunk)))
        :reconcile?  false)
      state)
    state))

(defn reconcile-chunks
  "Reconciles all chunks that are marked for reconciling.
  See `reconcile-chunk`."
  [state]
  (reduce reconcile-chunk state (:chunks state)))

(defn chunk-at-point
  "Returns the chunk at offset `point`. If the chunk was not previously annotated start
   annotating the chunk. Returns a vector `[state chunk]` with an updated version of `state`
   and the actual chunk at offset `point`."
  [state point]
  (if-let [[i _] (span/span-at-point (:chunks state) point)]
    (let [chunk (-> state :chunks (nth i))
          state (if (and (not (:separator? chunk))
                         (:reconcile? chunk))
                  (reconcile-chunk state (assoc chunk :reconcile? true))
                  state)
          chunk (-> state :chunks (nth i))]
      [state chunk])
    [state nil]))

(defn update
  "Applies the edit operations `delta` to `state`.
   Returns an updated version of `state`."
  [state delta]
  ;; Changes can be
  ;;  1. between two chunks A and B, in which case thetere are two possible
  ;;     scenarios:
  ;;       a. A remains unchanged and the spans of B and of all chunks
  ;;          that follow B have to be updated.
  ;;       b. A and B have to be merged together because the chunk
  ;;          separator isn't valid anymore. This also changes the
  ;;          spans and indices of all chunks that follow A and B.
  ;;  2. inside one chunk C, in which case C's span (end offset) and
  ;;     the span of all chunks that follow C must be updated to
  ;;     reflect the changes
  (let [text (apply-delta (:text state) delta)]
    (-> state
        (apply-delta-to-chunks delta)
        (assoc :text text)
        update-chunks
        detect-new-chunks)))

;;; -------------------------------------------------------------------
;;; Type annotations

;;; Some fns can't be type checked atm because of missing type
;;; annotations for clojure.core/{update,get,assoc}-in

(t/defalias ^{:doc/format :plaintext}
  Operation
  (t/HVec [(t/I (t/Value :insert)
                (t/Value :retain)
                (t/Value :delete))
           (t/I String t/Int)]))

(t/defalias ^{:doc/format :plaintext}
  Chunk
  (t/HMap :mandatory
          {:text        String
           :index       t/Int
           :span        span/Span
           :annotations (t/Option model/AnnotationMap)
           :reconcile?  Boolean
           :separator?  Boolean}))

(t/defalias ^{:doc/format :plaintext}
  State
  (t/HMap :mandatory
          {:text      String
           :mime-type String
           :chunks    (t/Vec Chunk)}))

(t/ann chunks-iter
       [(t/HVec [t/Int t/Int (t/Vec Chunk)]) String
        -> (t/HVec [t/Int t/Int (t/Vec Chunk)])])

(t/ann chunks
       [String -> (t/Vec Chunk)])

(t/ann initialize
       [String -> State])

(t/ann ^:no-check reconcile-chunk
       [State Chunk -> State])

(t/ann reconcile-chunks
       [State -> State])

(t/ann chunk-at-point
       [State t/Int -> (t/HVec [State (t/Option Chunk)])])

(t/ann ^:no-check subsequent-span-update-iter
       [Span (t/Vec Span) t/Int -> (t/Vec Span)])

(t/ann subsequent-span-update
       [(t/Vec Span) t/Int Span -> (t/Vec Span)])

(t/ann apply-delta-update-state
       [(t/Vec Span) t/Int t/Int Span Operation
        -> (t/Vec Span)])

(t/ann apply-delta-to-chunks-iter
       [(t/HVec [(t/Vec Span) (t/Vec Span) t/Int]) Operation
        -> (t/HVec [(t/Vec Span) (t/Vec Span) t/Int])])

(t/ann ^:no-check mark-chunks-for-reconciling
       [State t/Int (t/Option (t/Seq Span)) -> State])

(t/ann ^:no-check apply-delta-to-chunks
       [State (t/Option (t/Seq Operation)) -> State])

(t/ann ^:no-check update-chunk
       [State Chunk -> State])

(t/ann update-chunks
       [State -> State])

(t/ann detect-new-chunks
       [State -> State])

(t/ann update
       [State (t/Seq Operation) -> State])

(t/ann apply-delta-helper
       [(t/HVec [String (t/Vec String)]) Operation
        -> (t/HVec [String (t/Vec String)])])

(t/ann apply-delta
       [String (t/Seq Operation) -> String])
