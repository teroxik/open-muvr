package com.eigengo.lift.exercise

import akka.actor.{Props, Actor}
import com.eigengo.lift.Exercise.Exercise
import UserExercises._

/**
 * Companion object for the classifier
 */
object UserExercisesClassifier {
  def props(sessionProps: SessionProperties, modelProps: Props): Props =
    Props(new UserExercisesClassifier(sessionProps, modelProps))

  /**
   * ADT holding the classification result
   */
  sealed trait ClassifiedExercise

  /**
   * Known exercise with the given confidence, name and optional intensity
   * @param metadata the model metadata
   * @param confidence the confidence
   * @param exercise the exercise
   */
  case class FullyClassifiedExercise(metadata: ModelMetadata, confidence: Double, exercise: Exercise) extends ClassifiedExercise

  /**
    * Unknown exercise
   * @param metadata the model
   */
  case class UnclassifiedExercise(metadata: ModelMetadata) extends ClassifiedExercise

  /**
    * No exercise: ideally, a rest between sets, or just plain old not working out
   * @param metadata the model
   */
  case class NoExercise(metadata: ModelMetadata) extends ClassifiedExercise

  /**
   * Tap gesture
   */
  case object Tap extends ClassifiedExercise
}

/**
 * Match the received exercise data using the given model.
 */
class UserExercisesClassifier(sessionProperties: SessionProperties, modelProps: Props) extends Actor {

  // Issue "callback" (via sender actor reference) whenever we detect a tap gesture with a matching probability >= 0.80
  val model = context.actorOf(modelProps)

  override def receive: Receive = {
    // TODO: refactor code so that the following assumptions may be weakened further!
    case sdwls: ClassifyExerciseEvt =>
      require(
        sdwls.sensorData.nonEmpty,
        "at least one sensor locations are present in the `ClassifyExerciseEvt` instance and have data"
      )
      val sensorsGrouped = sdwls.sensorData.groupBy(_.location).mapValues(_.map(_.data))
      val (firstLocation, _) = sensorsGrouped.head
      val emptyDataValues: List[List[SensorData]] = sensorsGrouped(firstLocation).map(_.map(originalData ⇒
        AccelerometerData(originalData.samplingRate, originalData.values.map(_ ⇒ AccelerometerValue(0, 0, 0)))))
      val sensorMap: Map[SensorDataSourceLocation, List[List[SensorData]]] =
        Sensor.sourceLocations.map(location ⇒ (location, sensorsGrouped.getOrElse(location, emptyDataValues))).toMap
      val blockSize = sensorMap(SensorDataSourceLocationWrist).head.length
      require(
        sensorMap.values.forall(_.forall(_.length == blockSize)),
        "all sensor data location points have a common data length"
      )

      (0 until blockSize).foreach { block =>
        val sensorEvent = sensorMap.map { case (loc, data) => (loc, (0 until data.size).map(point => sensorMap(loc)(point)(block)).toVector) }.toMap

        model.tell(SensorNet(sensorEvent), sender())
      }

  }

}
