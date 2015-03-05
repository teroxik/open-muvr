package com.eigengo.lift.exercise

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentView
import com.eigengo.lift.common.{AutoPassivation, UserId}
import com.eigengo.lift.exercise.UserExercises.ExerciseSuggestionsSetEvt
import com.eigengo.lift.exercise.UserExercisesSuggestions.GetSuggestions

object UserExercisesSuggestions {
  /** The shard name */
  val shardName = "user-exercises-suggestions"

  /** The props to create the actor on a node */
  def props() = Props(classOf[UserExercisesSuggestions])

  /**
   * Query to provide suggestions for future exercise sessions
   */
  private case object GetSuggestions
}

/**
 * View that serves queries for user's suggested exercises
 */
class UserExercisesSuggestions
  extends PersistentView
  with ActorLogging
  with AutoPassivation {

  //Internal state
  private var suggestions = Suggestions.empty

  private val userId = UserId(self.path.name)
  override def viewId: String = s"user-exercises-suggestions-${userId.toString}"
  override def persistenceId: String = s"user-exercises-${userId.toString}"

  override def receive: Receive = events orElse queries

  def queries: Receive = {
    case GetSuggestions ⇒
      println(s"Here you go pal $suggestions")
      log.debug("GetSuggestions: from userspace.")
      sender() ! suggestions
  }

  def events: Receive = {
    case ExerciseSuggestionsSetEvt(sessions) ⇒
      println(s"Cheers pal $sessions")
      suggestions = sessions
  }
}
