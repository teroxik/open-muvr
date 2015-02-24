package com.eigengo.lift.exercise.classifiers.model

import akka.actor.Actor
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl._
import com.eigengo.lift.exercise.UserExercises.ModelMetadata
import com.eigengo.lift.exercise.UserExercisesClassifier.{UnclassifiedExercise, ClassifiedExercise}
import com.eigengo.lift.exercise.classifiers.ExerciseModel.{StableValue, QueryValue, Query}
import com.eigengo.lift.exercise.classifiers.workflows.ClassificationAssertions.BindToSensors
import com.eigengo.lift.exercise.{SensorNetValue, SessionProperties}
import com.eigengo.lift.exercise.classifiers.ExerciseModel

import scala.concurrent.{Future, ExecutionContext}

object EmptyExerciseModel {

  def apply(sessionProps: SessionProperties): EmptyExerciseModel =
    new EmptyExerciseModel(sessionProps)

  implicit val prover = new SMTInterface {
    // Random model performs no query simplification
    def simplify(query: Query)(implicit ec: ExecutionContext) = Future(query)

    // Random model always claims that query is satisfiable
    def satisfiable(query: Query)(implicit ec: ExecutionContext) = Future(true)

    // Random model always claims that query is valid
    def valid(query: Query)(implicit ec: ExecutionContext) = Future(true)
  }

}

/**
 * Empty exercise model. Does not recognize anything.
 *
 * @param sessionProps
 */
class EmptyExerciseModel(sessionProps: SessionProperties)
  extends ExerciseModel("empty", sessionProps)(EmptyExerciseModel.prover)
  with Actor {

  import FlowGraphImplicits._

  private val metadata = ModelMetadata(2)

  /**
   * Defined by implementing subclasses. Given a new event in our sensor trace, determines the next state that our model
   * evaluator will assume.
   *
   * @param current   current model evaluator state
   * @param event     new event received by the sensor network
   * @param lastState determines if this is the last event to be received by the sensor network or not
   */
  override protected def evaluateQuery(current: Query)(event: BindToSensors, lastState: Boolean): QueryValue =
    StableValue(result = true)

  /**
   * Defined by implementing subclasses. Configurable flow that determines the (optional) message sent back to the
   * UserExercisesProcessor.
   *
   * @param query  query that we have been requested to watch
   */
  protected def makeDecision(query: Query): Flow[QueryValue, Option[ClassifiedExercise]] =
    Flow[QueryValue]
      .map { _ ⇒
      None
    }

  /**
   * Defined by implementing subclasses. Configurable flow defined by implementing subclasses
   */
  override protected def workflow: Flow[SensorNetValue, BindToSensors] = {
    val in = UndefinedSource[SensorNetValue]
    val out = UndefinedSink[BindToSensors]

    PartialFlowGraph { implicit builder =>
      Source(ActorPublisher(self)) ~> Sink.ignore
    }.toFlow(in, out)
  }
}
