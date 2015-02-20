package com.eigengo.lift

import java.util.Date

object MuscleGroups {

  /**
   * Muscle group information
   *
   * @param key the key
   * @param title the title
   * @param exercises the suggested exercises
   */
  case class MuscleGroup(key: String, title: String, exercises: List[String])

  val supportedMuscleGroups = List(
    MuscleGroup(key = "legs",  title = "Legs",  exercises = List("squat", "leg press", "leg extension", "leg curl", "lunge")),
    MuscleGroup(key = "core",  title = "Core",  exercises = List("crunch", "side bend", "cable crunch", "sit up", "leg raises")),
    MuscleGroup(key = "back",  title = "Back",  exercises = List("pull up", "row", "deadlift", "hyper-extension")),
    MuscleGroup(key = "arms",  title = "Arms",  exercises = List("bicep curl", "hammer curl", "pronated curl", "tricep push down", "tricep overhead extension", "tricep dip", "close-grip bench press")),
    MuscleGroup(key = "chest", title = "Chest", exercises = List("chest press", "butterfly", "cable cross-over", "incline chest press", "push up")),
    MuscleGroup(key = "shoulders", title = "Shoulders", exercises = List("shoulder press", "lateral raise", "front raise", "rear raise", "upright row", "shrug")),
    MuscleGroup(key = "cardiovascular", title = "Cardiovascular", exercises = List("running", "cycling", "swimming", "elliptical", "rowing"))
  )
}

/**
 * The suggestion source ADT
 */
sealed trait SuggestionSource
object SuggestionSource {
  /** Suggestion based on user's exercise history */
  case object History extends SuggestionSource
  /** Suggestion based on user's exercise programme */
  case object Programme extends SuggestionSource
  /** Personal trainer's suggestion, along with notes */
  case class Trainer(notes: String) extends SuggestionSource
}

/**
 * A single suggestion needs a date and the source
 */
sealed trait Suggestion {
  /**
   * The date that the suggestion should be acted upon; e.g. do legs on 29th of February.
   * @return the date with the time element set to midnight
   */
  def date: Date

  /**
   * The source of the suggestion: is it based on the history, trainer's feedback or
   * some exercise programme?
   * @return the source
   */
  def source: SuggestionSource
}

/** Holds implementations of ``Suggestion`` */
object Suggestion {

  type MuscleGroupKey = String

  type ExerciseIntensity = Double

  /**
   * Suggests exercising
   * @param date the date
   * @param source the source
   * @param muscleGroupKeys the target muscle groups
   * @param intensity the intensity
   */
  case class Session(date: Date, source: SuggestionSource, muscleGroupKeys: Seq[MuscleGroupKey], intensity: ExerciseIntensity) extends Suggestion

  /**
   * Suggests resting
   * @param date the date
   * @param source the source
   */
  case class Rest(date: Date, source: SuggestionSource) extends Suggestion
}

/**
 * Wraps the list of Suggestions
 * @param suggestions the suggestions
 */
case class Suggestions(suggestions: List[Suggestion])