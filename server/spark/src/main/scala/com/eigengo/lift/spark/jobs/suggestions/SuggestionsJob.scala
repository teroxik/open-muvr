package com.eigengo.lift.spark.jobs.suggestions

import java.util.Date

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
import org.apache.spark.{SparkConf, SparkContext}
import spray.client.pipelining._
import spray.http.Uri.Path
import org.apache.spark.mllib.rdd.RDDFunctions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Try, _}

/**
 * Spark job suggesting exercises based on various parameters
 * Reads history from cassandra, uses trainers hints and programmes
 *
 * Currently a test class only suggesting random exercises
 */
class SuggestionsJob() extends Batch[Unit, Unit] with HttpClient with ExerciseMarshallers {

  override def name: String = "Test exercise suggestions"

  override def defaultParams(args: Array[String]): Unit = ()

  override def additionalConfig: (Config, SparkConf) => SparkConf = (c, conf) =>
    conf.set("spark.cassandra.connection.host", c.getString("cassandra.host"))
      .set("spark.cassandra.journal.keyspace", "akka")
      .set("spark.cassandra.journal.table", "messages")

  override def execute(sc: SparkContext, config: Config, params: Unit): Future[Either[String, Unit]] = {

    val result = Try {

      //TODO: Parameters
      val useHistory = 15
      val predictDays = 3

      val events = sc.eventTable().cache()

      getEligibleUsers(events)
        .map(pipeline(events, useHistory, predictDays))
        .collect()
        .map(x => {
          //TODO: This is run in the driver program. Running at workers results in serializationexception. Can be fixed?
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
    val inputData = preProcess(getPredictorInputData(events, user))
    val inputDataSize = inputData.count

    val now = new Date()

    val normalizedUseHistory = Math.min(useHistory, inputDataSize.toInt)

    val muscleKeyGroupsTrainingData = inputData
      .map(_._1)
      .sliding(normalizedUseHistory)
      .map(exercises => LabeledPoint(exercises.head, Vectors.dense(exercises.tail)))

    val intensityTrainingData = inputData
      .map(_._2)
      .sliding(normalizedUseHistory)
      .map(exercises => LabeledPoint(exercises.head, Vectors.dense(exercises.tail)))

    val muscleKeyGroupModel = NaiveBayes.train(muscleKeyGroupsTrainingData)
    val intensityModel = LinearRegressionWithSGD.train(intensityTrainingData, 100)

    var testData: Array[(Double, Double)] = Array()

    val predictions = for (i <- 0 to predictDays - 1) yield {
      testData = inputData
        .zipWithIndex()
        .filter(_._2 > inputDataSize - useHistory - 1 + i)
        .collect()
        .map(_._1) ++ testData.takeRight(i)

      val predictedMuscleKeyGroup = muscleKeyGroupModel.predict(Vectors.dense(testData.map(_._1)))
      val predictedIntensity = intensityModel.predict(Vectors.dense(testData.map(_._2)))

      (predictedMuscleKeyGroup, predictedIntensity, addDays(now, i + 1))
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
