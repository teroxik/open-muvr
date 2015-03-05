package com.eigengo.lift.spark.jobs.suggestions

import java.util.Arrays._
import java.util.{Date, UUID}

import akka.analytics.cassandra.JournalKey
import com.eigengo.lift.MuscleGroups
import com.eigengo.lift.exercise.UserExercises.SessionStartedEvt
import org.apache.spark.rdd.RDD

import scala.util.Try

private[suggestions] object SuggestionPipeline {

  private val weightedMuscleGroups = MuscleGroups.supportedMuscleGroups.zip(0d to 1 by 1d / MuscleGroups.supportedMuscleGroups.size)

  /**
   * The prediction pipeline consists of
   *
   * For each user do
   *   RawInputData => FilteredInputData => NormalizedInputData => Predictor => PredictorResult => DenormalizedPredictorResult
   */
  type PredictionPipeline = String => (String, DenormalizedPredictorResult)

  type RawInputData = RDD[(JournalKey, Any)]
  type FilteredInputData = RDD[SessionStartedEvt]
  type NormalizedInputData = RDD[(Double, Double)]
  type PredictorResult = Seq[(Double, Double, Date)]
  type DenormalizedPredictorResult = Seq[(String, Double, Date)]

  def addDays(date: Date, days: Int) =
    addMilliseconds(date, 3600000 * 24 * days)

  def addMilliseconds(date: Date, millis: Long) =
    new Date(date.getTime + millis)

  object PreProcessing {

    private def normalize(exercise: String): Double =
      weightedMuscleGroups.find(x => x._1.key.compareToIgnoreCase(exercise) == 0).head._2

    def preProcess(input: FilteredInputData): NormalizedInputData =
      input.flatMap(e => e.sessionProps.muscleGroupKeys.map(sp => (normalize(sp), e.sessionProps.intendedIntensity)))

    def getEligibleUsers(events: RawInputData, sessionEndedBefore: Long): RDD[String] = {
      events
        .flatMap {
          case (k, e) if e.isInstanceOf[SessionStartedEvt] => Some((k, e.asInstanceOf[SessionStartedEvt]))
          case _ => None
        }
        .filter(ke => ke._2.sessionProps.startDate.compareTo(addMilliseconds(new Date(), -sessionEndedBefore)) > 0)
        .map(_._1)
        .map(e => Try(UUID.fromString(e.persistenceId.takeRight(36))))
        .filter(_.isSuccess)
        .map(_.get.toString)
        .distinct()
    }

    def getPredictorInputData(events: RawInputData, userId: String): FilteredInputData =
      //TODO: Add exercise session start/end to be able to tell time of exercise
      events
        .filter {
          case (eu, _) if eu.persistenceId == s"user-exercises-${userId.toString}" => true
          case _ => false
        }
        .map(_._2)
        .flatMap {
          case e: SessionStartedEvt => Some(e)
          case _ => None
        }
  }

  object PostProcessing {
    def postProcess(in: PredictorResult): DenormalizedPredictorResult =
      in.map(d => (denormalize(d._1), d._2, d._3))

    private def denormalize(exercise: Double): String = {
      val foundIndex = binarySearch(weightedMuscleGroups.map(_._2).toArray, exercise)
      val insertIndex = -foundIndex - 1

      (if (insertIndex == 0) {
        weightedMuscleGroups.head
      } else if (insertIndex == weightedMuscleGroups.length) {
        weightedMuscleGroups.last
      } else if (foundIndex < 0) {
        weightedMuscleGroups(insertIndex - 1)
      } else {
        weightedMuscleGroups(foundIndex)
      })
      ._1.key
    }
  }
}
