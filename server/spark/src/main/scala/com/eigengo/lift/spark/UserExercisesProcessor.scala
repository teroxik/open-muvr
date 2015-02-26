/*
package com.eigengo.lift.spark

import java.util.{Date, UUID}
import com.eigengo.lift.Suggestion.{ExerciseName, ExerciseIntensity, MuscleGroupKey}
import com.eigengo.lift.Suggestions

import scodec.bits.BitVector

import scala.language.postfixOps

/**
 * Sensor data marker trait
 */
trait SensorData {
  def samplingRate: Int
  def values: List[SensorValue]
}

/**
 * Accelerometer data groups ``values`` at the given ``samplingRate``
 * @param samplingRate the sampling rate in Hz
 * @param values the values
 */
case class AccelerometerData(samplingRate: Int, values: List[AccelerometerValue]) extends SensorData

/**
 * Accelerometer data
 * @param x the x
 * @param y the y
 * @param z the z
 */
case class AccelerometerValue(x: Int, y: Int, z: Int) extends SensorValue

/**
 * Sensor value marker trait
 */
trait SensorValue

/**
 * Location of the sensor on the human body. Regardless of what the sensor measures, we
 * are interested in knowing its location on the body.
 *
 * Consider accelerometer data received from a sensor on the wrist and waist. If the user is
 * doing (triceps) dips, we expect the data from the wrist sensor to be farily flat, but the
 * data from the waist sensor to show that the user's body is moving up and down.
 *
 * Other types of sensor data are the same—assuming the sensors are just as capable of reliably
 * measuring it—regardless of where the sensor is located. Heart rate is the same whether it is
 * measured by a chest strap or by a watch.
 */
sealed trait SensorDataSourceLocation
/// sensor on a wrist: typically a smart watch
case object SensorDataSourceLocationWrist extends SensorDataSourceLocation {
  override def toString = "wrist"
}
/// sensor around user's waist: e.g. mobile in a pocket
case object SensorDataSourceLocationWaist extends SensorDataSourceLocation {
  override def toString = "waist"
}
/// sensor near the user's foot: e.g. shoe sensor
case object SensorDataSourceLocationFoot extends SensorDataSourceLocation {
  override def toString = "foot"
}
/// sensor near the user's chest: typically a HR belt
case object SensorDataSourceLocationChest extends SensorDataSourceLocation {
  override def toString = "chest"
}
/// sensor with unknown location or where the location does not make a difference
case object SensorDataSourceLocationAny extends SensorDataSourceLocation {
  override def toString = "any"
}

case class PacketWithLocation(sourceLocation: SensorDataSourceLocation, payload: BitVector)

case class MultiPacket(timestamp: Long, packets: List[PacketWithLocation]) {
  def withNewPacket(packet: PacketWithLocation): MultiPacket = copy(packets = packets :+ packet)
}

object MultiPacket {
  def single(timestamp: Long)(pwl: PacketWithLocation): MultiPacket = MultiPacket(timestamp, List(pwl))
}

/**
 * Unit of measure
 */
sealed trait MetricUnit

/**
 * Metric for the exercise
 * @param value the value
 * @param metricUnit the unit
 */
case class Metric(value: Double, metricUnit: MetricUnit)

/**
 * A single recorded exercise
 *
 * @param name the name
 * @param intensity the intensity, if known
 * @param metric the metric
 */
case class Exercise(name: ExerciseName, intensity: Option[Double], metric: Option[Metric])

/**
 * The exercise session props
 * @param startDate the start date
 * @param muscleGroupKeys the planned muscle groups
 * @param intendedIntensity the planned intensity
 */
case class SessionProperties(startDate: Date,
                             muscleGroupKeys: Seq[MuscleGroupKey],
                             intendedIntensity: ExerciseIntensity) {
  require(intendedIntensity > 0.0, "intendedIntensity must be between <0, 1)")
  require(intendedIntensity <= 1.0, "intendedIntensity must be between <0, 1)")

  import scala.concurrent.duration._

  /** At brutal (> .95) intensity, we rest for 15-ish seconds */
  private val brutalRest = 15

  /**
   * The duration between sets
   */
  lazy val restDuration: FiniteDuration = (1.0 / intendedIntensity * brutalRest).seconds
}

/**
 * The session identity
 * @param id the id
 */
case class SessionId(id: UUID) extends AnyVal {
  override def toString = id.toString
}
object SessionId {
  def apply(s: String): SessionId = SessionId(UUID.fromString(s))
  def randomId(): SessionId = SessionId(UUID.randomUUID())
}

case class UserId(id: UUID) extends AnyVal {
  override def toString: String = id.toString
}
object UserId {
  def randomId(): UserId = UserId(UUID.randomUUID())
  def apply(s: String): UserId = UserId(UUID.fromString(s))
}

/**
 * User + list of exercises companion
 */
object UserExercisesProcessor {

  /**
   * Remove a session identified by ``sessionId`` for user identified by ``userId``
   * @param userId the user identity
   * @param sessionId the session identity
   */
  case class UserExerciseSessionDelete(userId: UserId, sessionId: SessionId)

  /**
   * Abandons the session identified by ``userId`` and ``sessionId``
   * @param userId the user identity
   * @param sessionId the session identity
   */
  case class UserExerciseSessionAbandon(userId: UserId, sessionId: SessionId)

  /**
   * Replay all data received for a possibly existing ``sessionId`` for the given ``userId``
   * @param userId the user identity
   * @param sessionId the session identity
   * @param data all session data
   */
  case class UserExerciseSessionReplayProcessData(userId: UserId, sessionId: SessionId, data: Array[Byte])

  /**
   * Starts the replay all data received for a possibly existing ``sessionId`` for the given ``userId``
   * @param userId the user identity
   * @param sessionId the session identity
   * @param sessionProps the session props
   */
  case class UserExerciseSessionReplayStart(userId: UserId, sessionId: SessionId, sessionProps: SessionProperties)

  /**
   * User classified exercise start.
   * @param userId user
   * @param sessionId session
   * @param exercise the exercise
   */
  case class UserExerciseExplicitClassificationStart(userId: UserId, sessionId: SessionId, exercise: Exercise)

  /**
   * Sets the metric of all the exercises in the current set that don't have a metric yet. So, suppose the user is in
   * a set doing squats, and the system's events are
   *
   * - ExerciseEvt(squat) // without metric
   * - ExerciseEvt(squat) // without metric
   * - ExerciseEvt(squat) // without metric
   * *** UserExerciseSetExerciseMetric
   *
   * The system generates the ExerciseMetricSetEvt, and the views should add the metric to the previous three exercises
   *
   * @param userId the user identity
   * @param sessionId the session identity
   * @param metric the received metric
   */
  case class UserExerciseSetExerciseMetric(userId: UserId, sessionId: SessionId, metric: Metric)

  /**
   * Obtains a list of example exercises for the given session
   * @param userId the user
   * @param sessionId the session
   */
  case class UserExerciseExplicitClassificationExamples(userId: UserId, sessionId: SessionId)

  /**
   * User classification end
   * @param userId the user
   * @param sessionId the session
   */
  case class UserExerciseExplicitClassificationEnd(userId: UserId, sessionId: SessionId)

  /**
   * Sets the exercise suggestions for the given user id
   * @param userId the user identity
   * @param suggestions the suggestions
   */
  case class UserExerciseSetSuggestions(userId: UserId, suggestions: Suggestions)

  /**
   * Receive multiple packets of data for the given ``userId`` and ``sessionId``. The ``packets`` is a ZIP archive
   * containing multiple files, each representing a single packet.
   *
   *
   * The main notion is that all packets in the archive have been measured *at the same time*. It is possible that
   * the archive contains the following files.
   *
   * {{{
   * ad-watch.dat  (accelerometer data from the watch)
   * ad-mobile.dat (accelerometer data from the mobile)
   * ad-shoe.dat   (accelerometer data from a shoe sensor)
   * hr-watch.dat  (heart rate from the watch)
   * hr-strap.dat  (heart rate from a HR strap)
   * ...
   * }}}
   *
   * The files should be processed accordingly (by examining their content, not their file names), and the exercise
   * classifiers should use all available information to determine the exercise
   *
   * @param userId the user identity
   * @param sessionId the session identity
   * @param packet the archive containing at least one packet
   */
  case class UserExerciseDataProcessMultiPacket(userId: UserId, sessionId: SessionId, packet: MultiPacket)

  /**
   * Process exercise data for the given session
   * @param sessionId the sessionProps identifier
   * @param packet the bytes representing an archive with multiple exercise data bits
   */
  private case class ExerciseDataProcessMultiPacket(sessionId: SessionId, packet: MultiPacket)

  /**
   * User classified exercise.
   * @param sessionId session
   * @param exercise the exercise
   */
  private case class ExerciseExplicitClassificationStart(sessionId: SessionId, exercise: Exercise)

  /**
   * Obtain list of classification examples
   * @param sessionId the session
   */
  private case class ExerciseExplicitClassificationExamples(sessionId: SessionId)

  /**
   * Replay the ``sessionId`` by re-processing all data in ``data``
   * @param sessionId the session identity
   * @param data all session data
   */
  private case class ExerciseSessionReplayProcessData(sessionId: SessionId, data: Array[Byte])

  /**
   * Starts the replay the ``sessionId`` by re-processing all data in ``data``
   * @param sessionId the session identity
   * @param sessionProps the session props
   */
  private case class ExerciseSessionReplayStart(sessionId: SessionId, sessionProps: SessionProperties)

  /**
   * Sets the metric for the unmarked exercises in the currently open set
   * @param sessionId the session identity
   * @param metric the metric
   */
  private case class ExerciseSetExerciseMetric(sessionId: SessionId, metric: Metric)

  /**
   * User classified exercise.
   * @param sessionId session
   */
  private case class ExerciseExplicitClassificationEnd(sessionId: SessionId)

  /**
   * Exercise suggestions
   * @param suggestions the suggestions
   */
  private case class ExerciseSetSuggestions(suggestions: Suggestions)

  /**
   * Abandons the give exercise session
   * @param sessionId the session identity
   */
  private case class ExerciseSessionAbandon(sessionId: SessionId)

  /**
   * Starts the user exercise sessionProps
   * @param userId the user identity
   * @param sessionProps the sessionProps details
   */
  case class UserExerciseSessionStart(userId: UserId, sessionProps: SessionProperties)

  /**
   * Ends the user exercise sessionProps
   * @param userId the user identity
   * @param sessionId the generated sessionProps identity
   */
  case class UserExerciseSessionEnd(userId: UserId, sessionId: SessionId)

  /**
   * The sessionProps has started
   * @param sessionProps the sessionProps identity
   */
  private case class ExerciseSessionStart(sessionProps: SessionProperties)

  /**
   * The sessionProps has ended
   * @param sessionId the sessionProps identity
   */
  private case class ExerciseSessionEnd(sessionId: SessionId)

  /**
   * Remove the specified ``sessionId``
   * @param sessionId the session identity
   */
  private case class ExerciseSessionDelete(sessionId: SessionId)

  /**
   * Accelerometer data for the given sessionProps
   * @param sessionId the sessionProps identity
   * @param data the data
   */
  private case class ExerciseSessionData(sessionId: SessionId, data: AccelerometerData)
}
*/
