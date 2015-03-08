package com.eigengo.lift.exercise

import akka.actor.{ActorRef, ActorSystem}
import akka.contrib.pattern.ClusterSharding
import com.eigengo.lift.common.MicroserviceApp.BootedNode
import com.eigengo.lift.exercise.ExerciseBoot._
import spray.routing.Route

import scala.concurrent.ExecutionContext

case class ExerciseBoot(userExercises: ActorRef, userExercisesSessions: ActorRef, userExercisesStatistics: ActorRef) extends BootedNode {
  /**
   * Starts the route given the exercise boot
   * @param ec the execution context
   * @return the route
   */
  def route(ec: ExecutionContext): Route = exerciseRoute(userExercises, userExercisesSessions, userExercisesStatistics)(ec)

  override def api: Option[(ExecutionContext) ⇒ Route] = Some(route)
}

/**
 * Starts the actors in this microservice
 */
object ExerciseBoot extends ExerciseService {

  /**
   * Boot the exercise microservice
   * @param system the AS to boot the microservice in
   */
  def boot(notification: ActorRef, profile: ActorRef)(implicit system: ActorSystem): ExerciseBoot = {
    val userExercise = ClusterSharding(system).start(
      typeName = UserExercisesProcessor.shardName,
      entryProps = Some(UserExercisesProcessor.props(notification, profile)),
      idExtractor = UserExercisesProcessor.idExtractor,
      shardResolver = UserExercisesProcessor.shardResolver)
    val userExercisesSessions = ClusterSharding(system).start(
      typeName = UserExercisesSessions.shardName,
      entryProps = Some(UserExercisesSessions.props(notification, profile)),
      idExtractor = UserExercisesSessions.idExtractor,
      shardResolver = UserExercisesSessions.shardResolver)
    val userExercisesStatistics = ClusterSharding(system).start(
      typeName = UserExercisesStatistics.shardName,
      entryProps = Some(UserExercisesStatistics.props),
      idExtractor = UserExercisesStatistics.idExtractor,
      shardResolver = UserExercisesStatistics.shardResolver)

    ExerciseBoot(userExercise, userExercisesSessions, userExercisesStatistics)
  }

}
