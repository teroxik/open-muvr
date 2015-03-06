package com.eigengo.lift.exercise

import akka.actor.{ActorLogging, Props}
import akka.contrib.pattern.ShardRegion
import akka.persistence.{SnapshotOffer, PersistentView}
import com.eigengo.lift.Exercise.Suggestions
import com.eigengo.lift.common.{AutoPassivation, UserId}
import com.eigengo.lift.exercise.UserExercises.ExerciseSuggestionsSetEvt
import com.eigengo.lift.exercise.UserExerciseSuggestions.GetExerciseSuggestions
import scala.language.postfixOps
import scala.concurrent.duration._

object UserExerciseSuggestions {
  /** The shard name */
  val shardName = "user-exercises-suggestions"

  /** The props to create the actor on a node */
  def props() = Props(classOf[UserExerciseSuggestions])

  /**
   * Query to provide suggestions for future exercise sessions
   */
  private case object GetExerciseSuggestions

  /**
   * Query to provide suggestions for future exercise sessions
   */
  case class UserGetExerciseSuggestions(userId: UserId)

  /**
   * Extracts the identity of the shard from the messages sent to the coordinator. We have per-user shard,
   * so our identity is ``userId.toString``
   */
  val idExtractor: ShardRegion.IdExtractor = {
    case UserGetExerciseSuggestions(userId) ⇒ (userId.toString, GetExerciseSuggestions)
  }

  /**
   * Resolves the shard name from the incoming message.
   */
  val shardResolver: ShardRegion.ShardResolver = {
    case UserGetExerciseSuggestions(userId) ⇒ s"${userId.hashCode() % 10}"
  }
}

/**
 * View that serves queries for user's suggested exercises
 */
class UserExerciseSuggestions
  extends PersistentView
  with ActorLogging
  with AutoPassivation {

  //Internal state
  private var suggestions = Suggestions.empty

  //Passivation timeout
  context.setReceiveTimeout(120.seconds)

  private val userId = UserId(self.path.name)
  override def viewId: String = s"user-exercises-suggestions-${userId.toString}"
  override def persistenceId: String = s"user-exercises-${userId.toString}"

  override def autoUpdateInterval: FiniteDuration = 1.second
  override def autoUpdate: Boolean = true

  override def receive: Receive = events orElse queries

  def queries: Receive = {
    case GetExerciseSuggestions ⇒
      log.debug("GetExerciseSuggestions: from userspace.")
      sender() ! suggestions
  }

  def events: Receive = {
    case ExerciseSuggestionsSetEvt(suggest) ⇒
      log.debug(s"ExerciseSuggestionsSetEvt: suggestions set to $suggest")
      suggestions = suggest
      saveSnapshot(suggest)

    case SnapshotOffer(_, offeredSnapshot: Suggestions) ⇒
      log.debug("SnapshotOffer: suggestions")
      suggestions = offeredSnapshot
  }
}