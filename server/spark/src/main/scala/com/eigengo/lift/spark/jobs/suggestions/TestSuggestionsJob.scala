package com.eigengo.lift.spark.jobs.suggestions

import java.util.UUID

import com.eigengo.lift.{Suggestions, MuscleGroups, Suggestion}
import com.eigengo.lift.Suggestion.Session
import com.eigengo.lift.SuggestionSource.Programme
import com.eigengo.lift.spark.api.{ExerciseMarshallers, HttpClient}
import com.eigengo.lift.spark.jobs.Batch
import com.typesafe.config.Config
import org.apache.spark.{SparkConf, SparkContext}
import spray.client.pipelining._
import java.util.Date
import spray.http.Uri.Path
import akka.analytics.cassandra._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import scala.concurrent.ExecutionContext.Implicits.global

class TestSuggestionsJob() extends Batch[Unit, Unit] with HttpClient with ExerciseMarshallers {

  override def name: String = "Test exercise suggestions"

  override def defaultParams(args: Array[String]): Unit = ()

  override def additionalConfig: (Config, SparkConf) => SparkConf = (c, conf) =>
    conf.set("spark.cassandra.connection.host", c.getString("cassandra.host"))
      .set("spark.cassandra.journal.keyspace", "akka")
      .set("spark.cassandra.journal.table", "messages")

  override def execute(sc: SparkContext, config: Config, params: Unit): Future[Either[String, Unit]] = {

    val result = Try {
      println("CASSANDRA EVENT TABLE: ")
      sc.eventTable()
        .cache()
        .foreach { x =>
        val suggestions = Suggestions(List(suggestExercise(x._2.toString)))

        request(uri => Post(uri.withPath(Path(s"/exercise/$x/classification")), suggestions), config)
      }
    }

    Future(result match {
      case Success(_) => Right((): Unit)
      case Failure(e) => Left(e.toString)
    })
  }

  private def suggestExercise(userId: String): Suggestion = {
    Session(new Date(), Programme, Seq(MuscleGroups.supportedMuscleGroups.head.key), 1.0)
  }
}
