package com.eigengo.lift.spark.jobs.suggestions

import java.util.Date
import java.util.concurrent.TimeUnit

import akka.analytics.cassandra._
import com.eigengo.lift.Suggestion.Session
import com.eigengo.lift.SuggestionSource.Programme
import com.eigengo.lift.spark.api.{ExerciseMarshallers, HttpClient}
import com.eigengo.lift.spark.jobs.Batch
import com.eigengo.lift.spark.jobs.suggestions.SuggestionPipeline.{DenormalizedPredictorResult, RawInputData, PredictionPipeline}
import com.eigengo.lift.spark.jobs.suggestions.SuggestionPipeline.PostProcessing._
import com.eigengo.lift.spark.jobs.suggestions.SuggestionPipeline.PreProcessing._
import com.eigengo.lift.spark.jobs.suggestions.SuggestionPipeline._
import com.eigengo.lift.{Suggestion, Suggestions}
import com.typesafe.config.Config
import org.apache.spark.mllib.classification.NaiveBayes
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.{LinearRegressionWithSGD, LabeledPoint}
import org.apache.spark.SparkContext
import spray.client.pipelining._
import spray.http.Uri.Path
import org.apache.spark.mllib.rdd.RDDFunctions._
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Try, _}

/**
 * Spark job suggesting exercises based on various parameters
 * Reads history from cassandra, uses trainers hints and programmes
 *
 * Currently a naive bayes classifier used for muscle groups
 * and linear regression for intensities
 *
 * Training data are N previous sessions
 * Testing data for today+1 are N previous sessions
 * Testing data for today+2 are N-1 previous sessions and 1 prediction
 */
class SuggestionsJob() extends Batch[Unit, Unit] with HttpClient with ExerciseMarshallers {

  override def name: String = "Test exercise suggestions"

  override def execute(sc: SparkContext, config: Config, params: Unit): Future[Either[String, Unit]] = {

    val result = Try {

      val sessionEndedBefore = config
        .getDuration("jobs.suggestions.includeUsersSessionEndedBefore", TimeUnit.MILLISECONDS)
        .milliseconds
        .toMillis
      val historySize = config.getInt("jobs.suggestions.historySizeParameter")
      val futureSize = config.getInt("jobs.suggestions.futureSizeParameter")

      val events = sc.eventTable().cache()

      getEligibleUsers(events, sessionEndedBefore).collect()
        .map(pipeline(events, historySize, futureSize))
        .map(x => {
          //TODO: Currently run in the driver program. Run this in cluster and avoid serializationexception.
          submitResult(x._1, x._2, config)
        })
    }

    //TODO: Refactor error handling
    result match {
      case Success(f) =>
        Future.sequence(f.toList).map{ a =>
          val failed = a.filter(_.isLeft)
          if(failed.isEmpty) Right((): Unit) else Left(failed.mkString(","))
        }

      case Failure(e) => Future(Left(e.toString))
    }
  }

  private def pipeline(events: RawInputData, useHistory: Int, predictDays: Int): PredictionPipeline = { user =>
    val inputData = preProcess(getPredictorInputData(events, user).sortBy(_.sessionProps.startDate, false))
    val inputDataSize = inputData.count

    val now = new Date()

    val normalizedUseHistory = Math.min(useHistory, inputDataSize.toInt - 1)

    val muscleKeyGroupsTrainingData = inputData
      .map(_._1)
      .sliding(normalizedUseHistory + 1)
      .map(exercises => LabeledPoint(exercises.head, Vectors.dense(exercises.tail)))

    val intensityTrainingData = inputData
      .map(_._2)
      .sliding(normalizedUseHistory + 1)
      .map(exercises => LabeledPoint(exercises.head, Vectors.dense(exercises.tail)))

    val muscleKeyGroupModel = NaiveBayes.train(muscleKeyGroupsTrainingData)
    val intensityModel = LinearRegressionWithSGD.train(intensityTrainingData, 100)

    val indexedInputData = inputData.zipWithIndex().cache()
    var predictions: List[(Double, Double, Date)] = Nil

    for (i <- 0 to predictDays - 1) {
      val historyTestData = indexedInputData
        .filter(x => x._2 > inputDataSize - normalizedUseHistory - 1 + i)

      val padWithPredictions = normalizedUseHistory - historyTestData.count().toInt

      val paddedTestData = if(padWithPredictions > 0) {
        historyTestData.map(_._1).collect() ++
          predictions.take(Math.min(predictions.size, padWithPredictions)).map(x => (x._1, x._2)) ++
          Array.fill(Math.min(0, padWithPredictions - predictions.size))((0.5, 0.5))
      } else {
        historyTestData.map(_._1).collect()
      }

      require(paddedTestData.size == normalizedUseHistory)

      val predictedMuscleKeyGroup = muscleKeyGroupModel.predict(Vectors.dense(paddedTestData.map(_._1)))
      val predictedIntensity = intensityModel.predict(Vectors.dense(paddedTestData.map(_._2)))

      predictions = predictions.::((predictedMuscleKeyGroup, predictedIntensity, addDays(now, i + 1)))
    }

    (user, postProcess(predictions))
  }

  private def submitResult(userId: String, suggestions: DenormalizedPredictorResult, config: Config): Future[Either[String, String]] =
    request(
      uri => Post(uri.withPath(Path(s"/exercise/$userId/classification")), buildSuggestions(suggestions)),
      config)

  private def buildSuggestions(suggestions: DenormalizedPredictorResult): Suggestions =
    Suggestions(suggestions.map(s => buildSuggestion(s._3, s._1, s._2)).toList)

  private def buildSuggestion(date: Date, exercise: String, intensity: Double): Suggestion =
    Session(date, Programme, Seq(exercise), intensity)
}
