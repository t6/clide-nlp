package clide.nlp.assistant

import java.util.Collections
import clide.assistants.{AssistantControl, AssistantBehavior, Cursor}
import clide.collaboration.{Annotations, Operation}
import clide.models.{OpenedFile, SessionInfo}

/**
 * If you override any existing methods do not forget to call their
 * super-class methods.
 */
trait ReconcilingAssistantBehavior[State,Chunk]
  extends AssistantBehavior
  with ReconcilerStateTracker[State,Chunk] {
  //type AnnotationRequest = (OpenedFile, State, Chunk, Int)

  /**
   * Return the assistant control that is passed to your AssistantServer.
   * Use `control` to access it later.
   */
  protected def assistantControl: AssistantControl
  lazy val control: ReconcilingAssistantControl =
    new ReconcilingAssistantControl(assistantControl)

  /**
   * Return a IReconciler instance that defines the reconciling behavior of the
   * assistant
   */
  def reconciler: IReconciler[State,Chunk]

  def reconcileChunkAtPoint(session: SessionInfo,
                            file: OpenedFile,
                            point: Int) =
    for {
      (newState, Some(chunk)) <- chunkAtPoint(file, point)
    } yield {
      val v = (file, newState, chunk, point)
      rememberLastAnnotationPoint(session, file, point)
      enqueueAnnotationRequest(v)
    }

  /**
   * Implementing classes should pass the given request their own annotation
   * processing pipeline. This method should return as fast as possible, so defer
   * processing the request for later.
   *
   * If desired, an annotation request can be safely ignored.
   */
  def enqueueAnnotationRequest(request: AnnotationRequest)

  /**
   * @return the annotation streams the assistant can offer
   */
  def streams: Map[String,String]

  def offerStreams(file: OpenedFile): Unit =
    streams.toSeq.sortBy(_._1).foreach {
      case (k, v) => control.offerAnnotations(file, k, Some(v))
    }

  def fileOpened(file: OpenedFile): Unit = {
    track(file)
    offerStreams(file)
  }

  def fileActivated(file: OpenedFile): Unit = offerStreams(file)

  def fileClosed(file: OpenedFile): Unit = untrack(file)

  /**
   * called when a file in the assistants scope has been edited.
   * @param file the state of the file **after** the edit occured.
   * @param delta the operation that has been performed
   */
  def fileChanged(file: OpenedFile,
                  delta: Operation,
                  cursors: Seq[Cursor]): Unit = {
    control.workOnFile(file)
    update(file, delta)
    for(cursor <- cursors)
      reconcileChunkAtPoint(cursor.owner, cursor.file, cursor.anchor)
  }

  /**
   * called when some active collaborator moved the cursor in some file that
   * belongs to the assistants scope.
   */
  def cursorMoved(cursor: Cursor): Unit = {
    control.workOnFile(cursor.file)
    reconcileChunkAtPoint(cursor.owner, cursor.file, cursor.anchor)
  }

  /**
   * at least one client is interested in seeing the specified annotation stream
   */
  def annotationsRequested(file: OpenedFile,
                           name: String): Unit = {
    control.annotate(file, name, new Annotations())
    control.subscribe(file, name)
    // Reannotate last chunk to make the annotation visible
    reannotateLastPoint(file)
  }

  /**
   * all clients dropped their interest in seeing the specified annotation stream
   */
  def annotationsDisregarded(file: OpenedFile,
                             name: String): Unit = {
    control.unsubscribe(file, name)
    // Reannotate last chunk to hide the annotation for all collaborators
    reannotateLastPoint(file)
  }

  def reannotateLastPoint(file: OpenedFile): Unit =
    lastAnnotationPoint match {
      case Some(last@(f, _, _, _)) if f.info.id == file.info.id =>
        control.workOnFile(file)
        enqueueAnnotationRequest(last)
      case _ => Unit
    }
}
