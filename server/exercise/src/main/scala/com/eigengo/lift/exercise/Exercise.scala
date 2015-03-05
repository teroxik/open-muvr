package com.eigengo.lift

import java.util.Date

object Exercise {

  /** The exercise */
  @SerialVersionUID(1112l) type ExerciseName = String
  /** The exercise intensity 0..1 */
  @SerialVersionUID(1113l) type ExerciseIntensity = Double

  /** Muscle group */
  type MuscleGroupKey = String

  /** Requested classification */
  type RequestedClassification = String

  object RequestedClassification {

    /** Random classification */
    val RandomClassification = "RandomClassification"

    /** Manual classification */
    val ExplicitClassification = "ExplicitClassification"

  }

  /**
   * Adds much greater than and much less than operators to ``ExerciseIntensity`` instances
   * @param intensity the wrapped intensity
   */
  implicit class ExerciseIntensityOps(intensity: ExerciseIntensity) {
    private val factor = 0.33

    /**
     * Much greater than operator
     * @param that the intensity to compare
     * @return true if "this" is much greater than "that"
     */
    def >>(that: ExerciseIntensity): Boolean = intensity > that + (that * factor)

    /**
     * Much smaller than operator
     * @param that the intensity to compare
     * @return true if "this" is much smaller than "that"
     */
    def <<(that: ExerciseIntensity): Boolean = intensity < that - (that * factor)
  }

  /**
   * Unit of measure
   */
  @SerialVersionUID(1003l) sealed trait MetricUnit

  /**
   * All mass units of measure
   */
  @SerialVersionUID(1004l) object Mass {
    /// kg
    @SerialVersionUID(1005l) case object Kilogram extends MetricUnit
    // pound
  }

  /**
   * All distance units of measure
   */
  @SerialVersionUID(1006l) object Distance {
    /// km
    @SerialVersionUID(1007l) case object Kilometre extends MetricUnit

    // but can also include strange units like case object PoolLength
  }

  /**
   * Metric for the exercise
   * @param value the value
   * @param metricUnit the unit
   */
  @SerialVersionUID(1008l) case class Metric(value: Double, metricUnit: MetricUnit)

  /**
   * A single recorded exercise
   *
   * @param name the name
   * @param intensity the intensity, if known
   * @param metric the metric
   */
  @SerialVersionUID(1009l) case class Exercise(name: ExerciseName, intensity: Option[Double], metric: Option[Metric])

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
   * Companion object for suggestions providing initialization of default state
   */
  object Suggestions {
    val empty: Suggestions = Suggestions(List.empty)
  }

  /**
   * Wraps the list of Suggestions
   * @param suggestions the suggestions
   */
  case class Suggestions(suggestions: List[Suggestion])
}
