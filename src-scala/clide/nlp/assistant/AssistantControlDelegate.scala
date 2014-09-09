package clide.nlp.assistant

import clide.assistants.AssistantControl
import clide.models.OpenedFile
import clide.collaboration.Operation
import scala.concurrent.Future

class AssistantControlDelegate {
  type FileSubscriptionMap = Map[Long,Set[String]]

  var control: AssistantControl = null
  @volatile private var _subscriptions: FileSubscriptionMap = Map()

  def chat(message: String): Unit = chat(message, null)

  def chat(message: String, tpe: String): Unit =
    if(tpe == null) control.chat(message)
    else control.chat(message, Some(tpe))

  def annotate(file: OpenedFile, name: String, annotations: Object): Unit =
    control.annotate(file, name, Utilities.dataToAnnotations(annotations))

  def edit(file: OpenedFile, edit: Operation): Future[Unit] =
    // TODO: Clojure wrapper for Operation
    control.edit(file, edit)

  def stop(): Unit = control.stop()

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

  def offerAnnotations(file: OpenedFile, name: String, description: String) =
    control.offerAnnotations(file, name, if(description == null) None
                                         else Some(description))

  def workOnFile(file: OpenedFile): Unit = control.workOnFile(file)
  def doneWithFile(file: OpenedFile): Unit = control.doneWithFile(file)
  def failedInFile(file: OpenedFile, message: String) =
    control.failedInFile(file,
                         if(message == null) None
                         else Some(message))

  def openFile(id: Long): Future[OpenedFile] = control.openFile(id)
}
