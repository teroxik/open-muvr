package com.eigengo.lift.exercise.classifiers.model

import akka.actor.ActorLogging
import akka.stream.scaladsl._
import com.eigengo.lift.exercise.classifiers.ExerciseModel.Query
import com.eigengo.lift.exercise.classifiers.workflows.{ClassificationAssertions, GestureWorkflows}
import com.eigengo.lift.exercise._
import com.eigengo.lift.exercise.classifiers.ExerciseModel

/**
 * Gesture classification model.
 *
 * Essentially, we view our model traces as being streams here. As a result, all queries are evaluated (on the actual
 * stream) from the time point they are received by the model.
 */
abstract class StandardExerciseModel(sessionProps: SessionProperties, tapSensor: SensorDataSourceLocation, toWatch: Set[Query] = Set.empty)(implicit prover: SMTInterface)
  extends ExerciseModel("tap", sessionProps, toWatch)
  with StandardEvaluation
  with ActorLogging {

  import ClassificationAssertions._
  import FlowGraph.Implicits._

  // Workflow for recognising 'tap' gestures that are detected via `tapSensor`
  object Tap extends GestureWorkflows("tap", context.system.settings.config)

  /**
   * Monitor wrist sensor and add in tap gesture detection.
   */
  val workflow = {
    Flow() { implicit builder =>
      val classifier = Tap.identifyEvent
      val split = builder.add(Broadcast[SensorNetValue](2))
      val merge = builder.add(Zip[Set[Fact], SensorNetValue]())

      split ~> Flow[SensorNetValue]
        .mapConcat(_.toMap(tapSensor).find(_.isInstanceOf[AccelerometerValue]).asInstanceOf[Option[AccelerometerValue]].toList)
        .via(classifier.map(_.toSet)) ~> merge.in0

      split ~> merge.in1

      (split.in, merge.out)
    }.via(Flow[(Set[Fact], SensorNetValue)].map { case (facts, data) => BindToSensors(facts, Set(), Set(), Set(), Set(), data) })
  }

}
