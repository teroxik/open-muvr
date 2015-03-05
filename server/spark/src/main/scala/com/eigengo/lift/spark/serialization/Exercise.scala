package com.eigengo.lift

object Exercise {
  /** The exercise */
  @SerialVersionUID(1112l) type ExerciseName = String
  /** The exercise intensity 0..1 */
  @SerialVersionUID(1113l) type ExerciseIntensity = Double

  /** Muscle group */
  @SerialVersionUID(1020l) type MuscleGroupKey = String

  /** Requested classification */
  @SerialVersionUID(1021l) type RequestedClassification = String

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
}
