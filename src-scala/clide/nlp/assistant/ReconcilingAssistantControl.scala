package clide.nlp.assistant
import akka.event.LoggingAdapter
import clide.assistants.AssistantControl
import clide.collaboration.{Operation, Annotations}
import clide.models.OpenedFile

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}

class ReconcilingAssistantControl(control: AssistantControl) extends AssistantControl{
  override val log: LoggingAdapter = control.log
  override def chat(message: String, tpe: Option[String]): Unit = control.chat(message, tpe)
  override implicit def executionContext: ExecutionContext = control.executionContext
  override def stop(): Unit = control.stop()
  override def openFile(id: Long): Future[OpenedFile] = control.openFile(id)
  override def edit(file: OpenedFile, edit: Operation): Future[Unit] = control.edit(file, edit)
  override def failedInFile(file: OpenedFile, message: Option[String]): Unit = control.failedInFile(file, message)
  override def workOnFile(file: OpenedFile): Unit = control.workOnFile(file)
  override def annotate(file: OpenedFile, name: String, annotations: Annotations, delay: FiniteDuration = Duration.Zero): Unit =
    control.annotate(file, name, annotations, delay)
  override def offerAnnotations(file: OpenedFile, name: String, description: Option[String]): Unit =
    control.offerAnnotations(file, name, description)
  override def doneWithFile(file: OpenedFile): Unit = control.doneWithFile(file)

  type FileSubscriptionMap = Map[Long,Set[String]]

  @volatile private var _subscriptions: FileSubscriptionMap = Map()

  def unsubscribe(file: OpenedFile, name: String): Unit = {
    val subscriptionSet = _subscriptions.getOrElse(file.info.id, Set()) - name
    _subscriptions = _subscriptions.updated(file.info.id, subscriptionSet)
  }
  def subscribe(file: OpenedFile, name: String): Unit = {
    val subscriptionSet = _subscriptions.getOrElse(file.info.id, Set()) + name
    _subscriptions = _subscriptions.updated(file.info.id, subscriptionSet)
  }

  def subscriptions(): FileSubscriptionMap = _subscriptions

  def isSubscribed(file: OpenedFile, name: String): Boolean = _subscriptions.getOrElse(file.info.id, Set()).contains(name)
}
