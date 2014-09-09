package clide.nlp.assistant

import clide.collaboration.Operation
import clide.models.{OpenedFile, SessionInfo}

/**
 *
 */
trait IReconciler[State,Chunk] {
  def initialize(mimeType: String, text: String): State
  def update(state: State, delta: Operation): State
  def chunkAtPoint(state: State, point: Integer): Option[(State, Option[Chunk])]
}
