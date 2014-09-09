package clide.nlp.assistant

import clide.collaboration.{Retain, Insert, Delete, Operation}
import clojure.java.api.Clojure
import clojure.lang.IFn

/**
 * An IReconciler implementation that wraps the clojure functions
 * from `clide.nlp.assistant.reconciler`
 */
class Reconciler extends IReconciler[Object,Object] {
  private val require = Clojure.`var`("clojure.core", "require")
  require.invoke(Clojure.read("clide.nlp.assistant.reconciler"))

  private val reconcilerInit = Clojure.`var`(
    "clide.nlp.assistant.reconciler",
    "initialize"
  )

  private val reconcilerUpdate = Clojure.`var`(
    "clide.nlp.assistant.reconciler",
    "update"
  )

  private val reconcilerChunkAtPoint = Clojure.`var`(
    "clide.nlp.assistant.reconciler",
    "chunk-at-point"
  )

  override def initialize(mimeType: String, text: String): Object = {
    reconcilerInit.invoke(mimeType, text)
  }

  override def chunkAtPoint(state: Object, point: Integer): Option[(Object,Option[Object])] = {
    val resultV = reconcilerChunkAtPoint.invoke(state, point).asInstanceOf[java.util.List[Object]]
    val newState = resultV.get(0)
    val chunk = resultV.get(1)
    Some((newState, Some(chunk)))
  }

  override def update(state: Object, delta: Operation): Object =
    reconcilerUpdate.invoke(state, Utilities.operationToData(delta))
}
