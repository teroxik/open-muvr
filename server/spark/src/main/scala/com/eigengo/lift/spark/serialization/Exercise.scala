package com.eigengo.lift

object Exercise {
  /** The exercise */
  type ExerciseName = String
  /** The exercise intensity 0..1 */
  type ExerciseIntensity = Double

  /** Muscle group */
  type MuscleGroupKey = String

  /** Requested classification */
  type RequestedClassification = String

  /**
   * Unit of measure
   */
  sealed trait MetricUnit

  /**
   * All mass units of measure
   */
  object Mass {
    /// kg
    case object Kilogram extends MetricUnit
    // pound
  }

  /**
   * All distance units of measure
   */
  object Distance {
    /// km
    case object Kilometre extends MetricUnit

    // but can also include strange units like case object PoolLength
  }

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
}
