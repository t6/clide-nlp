(ns clide.nlp.assistant.reconciler-expectations
  (:refer-clojure :exclude [contains?])
  (:require [clojure.set :as set]
            [expectations :refer :all]
            [clide.nlp.dev.expectations-utils :refer :all]
            [clide.nlp.assistant.reconciler :refer :all]))

;;; -------------------------------------------------------------------
;;; Test setup

(def text "This is a text.

----

With three chunks.")

;;; -------------------------------------------------------------------
;;; chunks

(expect 3 (count (dash-paragraph-chunker text)))
(expect (more-of [x y z]
          {:index       0
           :text        "This is a text.\n"
           :annotations nil
           :separator?  false
           ;; span [0 16] means the chunks goes from offset 0 up to
           ;; (and not including) offset 16
           :span        [0 16]}
          (in x)

          {:separator?  true
           :index       1
           :annotations nil
           :span        [16 22]}
          (in y)

          {:text        "\nWith three chunks."
           :index       2
           :annotations nil
           :separator?  false
           :span        [22 41]}
          (in z))
  (dash-paragraph-chunker text))

;;; -------------------------------------------------------------------
;;; initialize

(let [state (initialize text)]
  (expect {:text text} (in state))
  (expect (map :text (dash-paragraph-chunker text)) (map :text (:chunks state))))

;;; -------------------------------------------------------------------
;;; chunk-at-point

(expect {:text "This is a text.\n"}
  (in (second (chunk-at-point (initialize text) 0))))

(expect nil
  (second (chunk-at-point (initialize text) 10000)))

(expect {:separator? true}
  (in (second (chunk-at-point (initialize text) 16))))

;; make sure that the reconciler annotates chunks (that are not separators)
(defn annotated?
  [[state chunk]]
  (some?
    (when state
      (:annotations chunk))))

(expect (more->
          annotated?
          (chunk-at-point 0)

          ;; this is a separator so it should not be annotated
          (complement annotated?)
          (chunk-at-point 16))
  (initialize text))

;;; -------------------------------------------------------------------

;; check if adding a chunk at the end of a text works
(let [text "A\n----\nB"
      delta [[:retain (count text)]
             [:insert "\n----\nC"]
             [:retain 0]]]
  (expect 3 (count (:chunks (initialize text))))
  (expect 5 (count (:chunks (update (initialize text) delta)))))

;; check if adding new text to the last chunk works
(let [text "A\n----\nB"
      delta [[:retain (count text)]
             [:insert "C"]
             [:retain 0]]]
  (expect 3 (count (:chunks (initialize text))))
  (expect 3 (count (:chunks (update (initialize text) delta))))
  (expect "BC" (:text (last (:chunks (update (initialize text) delta))))))

;; check if deleting from a chunk at the end of a text works
(let [text "A\n----\nB"
      delta [[:retain (count text)]
             [:delete 1]
             [:retain 0]]]
  (expect 3 (count (:chunks (initialize text))))
  (expect 3 (count (:chunks (update (initialize text) delta))))
  (expect "" (:text (last (:chunks (update (initialize text) delta))))))

;; check if deleting the last chunk at the end of a text works
(let [text "A\n----\nB"
      delta [[:retain (count "A\n----")]
             [:delete 2]
             [:retain 0]]]
  (expect 3 (count (:chunks (initialize text))))
  (expect 1 (count (:chunks (update (initialize text) delta))))
  (expect "A\n----" (:text (last (:chunks (update (initialize text) delta))))))

;;; -------------------------------------------------------------------
;;; apply-delta

(expect-let [text "A\n----\nB"]
  "A---\nB"
  (apply-delta text
               [[:retain 1]
                [:delete 2]
                [:retain (- (count text) 3)]]))

(expect "Hi."
  (from-each [delta [[[:insert "Hi."]]
                     [[:insert "Hi."] ; negative retains are ignored
                      [:retain -3]]
                     [[:insert "Hi."] ; excessive retains => retain until end of string
                      [:retain 10000]]
                     [[:insert "Hi."]
                      [:delete -1]] ; negative deletes should be ignored
                     ]]
    (apply-delta "" delta)))

;; excessive deletes, delete until end of string
(expect "Hi."
  (apply-delta "bla" [[:insert "Hi."]
                      [:delete 1000]]))

;; delete
(expect-let [text "his is a test."]
  text
  (apply-delta "This is a test."
               [[:delete 1]
                [:retain (count text)]]))

(expect-let [text "Thisisatest."]
  text
  (apply-delta "This is a test."
               [[:retain 4]
                [:delete 1]
                [:retain 2]
                [:delete 1]
                [:retain 1]
                [:delete 1]
                [:retain (- (count text) 7)]]))

;; insert
(expect-let [text "This is a test."]
  "This is an interesting test."
  (apply-delta text
               [[:retain 9]
                [:insert "n interesting"]
                [:retain (- (count text) 9)]]))

(expect-let [text "This is a test."]
  "This is an interesting test."
  (apply-delta text [[:retain 9]
                     [:insert "n"]
                     [:retain 1]
                     [:insert "interesting "]
                     [:retain (- (count text) 10)]]))

;; mixing retain, insert, delete
(expect-let [text "This is a test."]
  "This is the text that tests `apply-delta`."
  (apply-delta text [[:retain 8]
                     [:delete 2]
                     [:insert "the "]
                     [:retain 2]
                     [:delete 2]
                     [:insert "xt"]
                     [:delete 1]
                     [:insert " that tests"]
                     [:insert " `apply-delta`."]
                     [:retain (- (count text) 15)]]))

;;; -------------------------------------------------------------------
;;; update

(defn chunks->text
  "Joins the text of all chunks. The result should equal the state's :text value."
  [state]
  (clojure.string/join (map :text (:chunks state))))

;; updating the reconciler state should apply the delta
(expect-let [text "This is a test. \n----\n Hi."
             delta [[:retain 1] [:insert "bla"] [:retain 21] [:delete 1] [:insert "hi"] [:retain 3]]]
  (apply-delta text delta)
  (-> (initialize text) (update delta) chunks->text))
