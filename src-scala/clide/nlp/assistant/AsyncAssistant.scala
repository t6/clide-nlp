package clide.nlp.assistant

import clide.assistants.{AssistantServer, AssistantBehavior, AssistantControl}
import clide.models.{ProjectInfo, SessionInfo, OpenedFile}
import clojure.java.api.Clojure
import clojure.lang.IFn

import scala.collection.immutable.Set

class AsyncAssistantServer(reconciler: IReconciler[Object,Object],
                           channel: Object,
                           streams: Map[String,String])
  extends AssistantServer((c: AssistantControl) =>
    new AsyncAssistantBehavior(c, reconciler, channel, streams))

case class AsyncAssistantBehavior(assistantControl: AssistantControl,
                                  reconciler: IReconciler[Object,Object],
                                  channel: Object,
                                  streams: Map[String,String])
  extends AssistantBehavior
  with ReconcilingAssistantBehavior[Object,Object] {

  val require = Clojure.`var`("clojure.core", "require")
  require.invoke(Clojure.read("clojure.core.async"))
  val put = Clojure.`var`("clojure.core.async", ">!!")

  // HACK: A lot of this behavior is defined by external Clojure functions.
  // They need access to our assistant control object. We reuse the channel
  // to make the assistant behavior available to them. We cannot access the
  // behavior otherwise, because it is hidden and constructed by the
  // AsyncAssistantServer.
  //
  // After constructing an AsyncAssistantServer make sure to immediately take
  // the reference to the behavior from the channel, before starting
  // any loops that read from it.
  put.invoke(channel, this)

  def mimeTypes: Set[String] = Set("text/plain", "text/x-clojure")

  /**
   * Implementing classes should pass the given request their own annotation
   * processing pipeline. This method should return as fast as possible, so defer
   * processing the request for later.
   *
   * If desired, an annotation request can be safely ignored.
   */
  def enqueueAnnotationRequest(request: AnnotationRequest): Unit =
    put.invoke(channel, request)

  override def start(project: ProjectInfo): Unit = Unit
  override def collaboratorLeft(who: SessionInfo): Unit = Unit
  override def stop: Unit = Unit
  override def receiveChatMessage(from: String,
                                  msg: String,
                                  tpe: Option[String],
                                  timestamp: Long): Unit = Unit
  override def collaboratorJoined(who: SessionInfo): Unit = Unit
  override def fileInactivated(file: OpenedFile): Unit = Unit
}
