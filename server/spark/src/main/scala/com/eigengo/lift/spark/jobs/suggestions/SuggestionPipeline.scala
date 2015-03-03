package com.eigengo.lift.spark.jobs.suggestions

import java.util.Arrays._
import java.util.{Date, UUID}

import akka.analytics.cassandra.JournalKey
import com.eigengo.lift.MuscleGroups
import com.eigengo.lift.exercise.UserExercises.ExerciseEvt
import org.apache.spark.rdd.RDD

import scala.math.BigDecimal.RoundingMode
import scala.util.{Try, Random}

object SuggestionPipeline {

  private val weightedMuscleGroups = MuscleGroups.supportedMuscleGroups.zip(0d to 1 by 1d / MuscleGroups.supportedMuscleGroups.size)

  /**
   * The prediction pipeline consists of
   *
   * For each user do
   *   RawInputData => FilteredInputData => NormalizedInputData => Predictor => PredictorResult => DenormalizedPredictorResult
   */
  type PredictionPipeline = String => (String, DenormalizedPredictorResult)

  type RawInputData = RDD[(JournalKey, Any)]
  type FilteredInputData = RDD[ExerciseEvt]
  type NormalizedInputData = RDD[Double]
  type Predictor = (String, NormalizedInputData, Int, Int) => PredictorResult
  type PredictorResult = Seq[(Double, Date)]
  type DenormalizedPredictorResult = Seq[(String, Date)]

  object PreProcessing {

    private def normalize(exercise: String): Double =
      weightedMuscleGroups.find(x => x._1.exercises.contains(exercise)).head._2

    def preProcess(input: FilteredInputData): NormalizedInputData =
      input.map(e => normalize(e.exercise.name))

    def getEligibleUsers(events: RawInputData): RDD[String] = {
      events
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
          case e: ExerciseEvt => Some(e)
          case _ => None
        }

    def usePredictor(inputData: FilteredInputData, useHistory: Int) =
      inputData.count() >= useHistory
  }

  object Processing {
    def randomExercise(r: Random) =
      weightedMuscleGroups(r.nextInt(weightedMuscleGroups.size))._2

    private def randomIntensity(r: Random) =
      BigDecimal(r.nextDouble()).setScale(2, RoundingMode.HALF_UP).toDouble
  }

  object PostProcessing {
    def postProcess(in: PredictorResult): DenormalizedPredictorResult =
      in.map(d => (denormalize(d._1), d._2))

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
