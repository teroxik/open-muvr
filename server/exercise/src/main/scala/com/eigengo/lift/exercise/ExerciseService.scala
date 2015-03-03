package com.eigengo.lift.exercise

import java.util.{Date, UUID}

import akka.actor.ActorRef
import spray.routing.Directives

import scala.concurrent.ExecutionContext

trait ExerciseService extends Directives with ExerciseMarshallers {
  import akka.pattern.ask
  import com.eigengo.lift.common.Timeouts.defaults._
  import UserExercisesProcessor._
  import UserExercisesSessions._
  import UserExercisesStatistics._

  def exerciseRoute(userExercisesProcessor: ActorRef, userExercisesSessions: ActorRef, userExercisesStatistics: ActorRef)(implicit ec: ExecutionContext) =
    path("exercise" / "musclegroups") {
      get {
        complete {
          UserExercisesStatistics.supportedMuscleGroups
        }
      }
    } ~
    path("exercise" / UserIdValue) { userId ⇒
      get {
        parameters('startDate.as[Date], 'endDate.as[Date]) { (startDate, endDate) ⇒
          complete {
            (userExercisesSessions ? UserGetExerciseSessionsSummary(userId, startDate, endDate)).mapTo[List[SessionSummary]]
          }
        } ~
        parameter('date.as[Date]) { date ⇒
          complete {
            (userExercisesSessions ? UserGetExerciseSessionsSummary(userId, date, date)).mapTo[List[SessionSummary]]
          }
        } ~
        complete {
          (userExercisesSessions ? UserGetExerciseSessionsDates(userId)).mapTo[List[SessionDate]]
        }
      }
    } ~
    path("exercise" / UserIdValue / "start") { userId ⇒
      post {
        handleWith { sessionProps: SessionProperties ⇒
          (userExercisesProcessor ? UserExerciseSessionStart(userId, sessionProps)).mapRight[UUID]
        }
      }
    } ~
    path("exercise" / UserIdValue / SessionIdValue / "end") { (userId, sessionId) ⇒
      post {
        complete {
          (userExercisesProcessor ? UserExerciseSessionEnd(userId, sessionId)).mapRight[Unit]
        }
      }
    } ~
    path("exercise" / UserIdValue / SessionIdValue) { (userId, sessionId) ⇒
      get {
        complete {
          (userExercisesSessions ? UserGetExerciseSession(userId, sessionId)).mapTo[Option[ExerciseSession]]
        }
      } ~
      put {
        handleWith { mp: MultiPacket ⇒
          (userExercisesProcessor ? UserExerciseDataProcessMultiPacket(userId, sessionId, mp)).mapRight[Unit]
        }
      } ~
      delete {
        complete {
          (userExercisesProcessor ? UserExerciseSessionDelete(userId, sessionId)).mapRight[Unit]
        }
      }
    } ~
    path("exercise" / UserIdValue / SessionIdValue / "metric") { (userId, sessionId) ⇒
      post {
        handleWith { metric: Metric ⇒
          userExercisesProcessor ! UserExerciseSetExerciseMetric(userId, sessionId, metric)
          ()
        }
      }
    } ~
    path("exercise" / UserIdValue / SessionIdValue / "replay") { (userId, sessionId) ⇒
      post {
        handleWith { sessionProps: SessionProperties ⇒
          (userExercisesProcessor ? UserExerciseSessionReplayStart(userId, sessionId, sessionProps)).mapRight[UUID]
        }
      } ~
      put {
        ctx ⇒ ctx.complete {
          (userExercisesProcessor ? UserExerciseSessionReplayProcessData(userId, sessionId, ctx.request.entity.data.toByteArray)).mapRight[Unit]
        }
      }
    } ~
    path("exercise" / UserIdValue / SessionIdValue / "abandon") { (userId, sessionId) ⇒
      post {
        complete {
          (userExercisesProcessor ? UserExerciseSessionAbandon(userId, sessionId)).mapRight[Unit]
        }
      }
    } ~
    path("exercise" / UserIdValue / "classification") { userId ⇒
      get {
        complete {
          (userExercisesStatistics ? UserExerciseExplicitClassificationExamples(userId, None)).mapTo[List[Exercise]]
        }
      } ~
      post {
        handleWith { suggestions: Suggestions ⇒
          (userExercisesProcessor ? UserExerciseSetSuggestions(userId, suggestions)).mapRight[Unit]
        }
      }
    } ~
    path("exercise" / UserIdValue / SessionIdValue / "classification") { (userId, sessionId) ⇒
      get {
        complete {
          (userExercisesStatistics ? UserExerciseExplicitClassificationExamples(userId, Some(sessionId))).mapTo[List[Exercise]]
        }
      } ~
      post {
        parameter('exerciseName.as[String]) { exerciseName ⇒
          complete {
            userExercisesProcessor ! UserExerciseExplicitClassificationStart(userId, sessionId, exerciseName)
            ()
          }
        }
      } ~
      delete {
        complete {
          userExercisesProcessor ! UserExerciseExplicitClassificationEnd(userId, sessionId)
          ()
        }
      }
    }
}
