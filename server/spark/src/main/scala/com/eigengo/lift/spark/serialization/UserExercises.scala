package com.eigengo.lift.exercise

import com.eigengo.lift.Exercise.Exercise

object UserExercises {

  /**
   * Model version and other metadata
   * @param version the model version
   */
  @SerialVersionUID(1000l) case class ModelMetadata(version: Int)

  /**
   * The MD companion
   */
  @SerialVersionUID(1001l) object ModelMetadata {
    /** Special user-classified metadata */
    val user = ModelMetadata(-1231344)
  }

  /**
   * The session has started
   * @param sessionId the session identity
   * @param sessionProps the session props
   */
  @SerialVersionUID(1018l) case class SessionStartedEvt(sessionId: SessionId, sessionProps: SessionProperties)

  /**
   * Exercise event received for the given session with model metadata and exercise
   * @param sessionId the session identity
   * @param metadata the model metadata
   * @param exercise the result
   */
  @SerialVersionUID(1002l) case class ExerciseEvt(sessionId: SessionId, metadata: ModelMetadata, exercise: Exercise)
}
