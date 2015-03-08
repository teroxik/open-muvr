package com.eigengo.lift.exercise

import java.util.{Date, UUID}

import com.eigengo.lift.Exercise._

/**
 * The session identity
 * @param id the id
 */
@SerialVersionUID(1010l) case class SessionId(id: UUID) extends AnyVal {
  override def toString = id.toString
}

@SerialVersionUID(1011l) object SessionId {
  def apply(s: String): SessionId = SessionId(UUID.fromString(s))
  def randomId(): SessionId = SessionId(UUID.randomUUID())
}

/**
 * The exercise session props
 * @param startDate the start date
 * @param muscleGroupKeys the planned muscle groups
 * @param intendedIntensity the planned intensity
 */
@SerialVersionUID(1022l) case class SessionProperties(
    startDate: Date,
    muscleGroupKeys: Seq[MuscleGroupKey],
    intendedIntensity: ExerciseIntensity,
    classification: RequestedClassification) {
  require(intendedIntensity >  0.0, "intendedIntensity must be between <0, 1)")
  require(intendedIntensity <= 1.0, "intendedIntensity must be between <0, 1)")

  import scala.concurrent.duration._

  /** At brutal (> .95) intensity, we rest for 15-ish seconds */
  private val brutalRest = 15

  /**
   * The duration between sets
   */
  lazy val restDuration: FiniteDuration = (1.0 / intendedIntensity * brutalRest).seconds
}