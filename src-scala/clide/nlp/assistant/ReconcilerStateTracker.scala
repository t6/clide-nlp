package clide.nlp.assistant

import clide.collaboration.Operation
import clide.models.{OpenedFile, SessionInfo}

/**
 * Keeps track of the reconciler state changes for multiple files.
 * This class is used by `ReconcilingAssistant` and should be considered an implementation detail.
 */
trait ReconcilerStateTracker[State,Chunk] {
  type AnnotationRequest = (OpenedFile, State, Chunk, Int)
  type AnnotationPoint = (OpenedFile,Int)

  def reconciler: IReconciler[State,Chunk]

  @volatile private var lastAnnotationPointAndFile: Option[AnnotationPoint] = None

  def rememberLastAnnotationPoint(session: SessionInfo,
                                  file: OpenedFile,
                                  point: Int): Unit =
    lastAnnotationPointAndFile = Some((file, point))

  def lastAnnotationPoint: Option[AnnotationRequest] =
    for {
      (file, point) <- lastAnnotationPointAndFile
      (state, Some(chunk)) <- chunkAtPoint(file, point)
    } yield (file, state, chunk, point)

  // A map from file id to current reconciler state
  @volatile private var state: Map[Long,State] = Map()

  def track(file: OpenedFile) = {
    state = state.updated(file.info.id, reconciler.initialize(file.info.mimeType.orNull, file.state))
  }

  def untrack(file: OpenedFile) =
    state = state - file.info.id

  def chunkAtPoint(file: OpenedFile,
                   point: Int): Option[(State, Option[Chunk])] =
    for {
      fileState <- state.get(file.info.id)
      (newState, Some(chunk)) <- reconciler.chunkAtPoint(fileState, point)
    } yield {
      state = state.updated(file.info.id, newState)
      (newState, Some(chunk))
    }

  def update(file: OpenedFile,
             delta: Operation) = for {
    oldState <- state.get(file.info.id)
    newState = reconciler.update(oldState, delta)
  } yield {
    state = state.updated(file.info.id, newState)
  }

  def reset() = state = Map()
}
