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
import akka.persistence.PersistentRepr
import com.eigengo.lift.spark.api.HttpClient
import com.typesafe.config.Config
import org.apache.spark.{SparkConf, SparkContext}
import akka.analytics.cassandra._
import com.datastax.spark.connector._
import spray.http.Uri.Path
import spray.client.pipelining._

import scala.concurrent.Future
import scala.util._

import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global

class TestSuggestionsJob() extends Batch[Unit, Unit] with HttpClient with ExerciseMarshallers {

  override def name: String = "Test exercise suggestions"

  override def defaultParams(args: Array[String]): Unit = ()

  override def additionalConfig: (Config, SparkConf) => SparkConf = (c, conf) =>
    conf.set("spark.cassandra.connection.host", c.getString("cassandra.host"))
      .set("spark.cassandra.journal.keyspace", "akka")
      .set("spark.cassandra.journal.table", "messages")

  override def execute(sc: SparkContext, config: Config, params: Unit): Future[Either[String, Unit]] = {
    val journalKeyEventPair = (persistenceId: String, partition: Long, sequenceNr: Long, message: PersistentRepr) =>
      (JournalKey(persistenceId, partition, sequenceNr), message.payload)

    println("TRYING")

    val result = Try {
      val events = sc
        .cassandraTable("akka", "messages")
        .select("processor_id", "partition_nr", "sequence_nr", "message")
        .as(journalKeyEventPair)
        .map{x => println(x); x}
        .map(_._1.persistenceId)
        .filter(x => x.startsWith("user-profile-") && !x.contains("processor"))
        .map{x => println(x); x}
        .map(_.drop(13))
        .distinct()
        .map(x => {
          val suggestions = Suggestions(List(suggestExercise(), suggestExercise(), suggestExercise()))
          println(s"SUGGESTIONS FOR $x ARE $suggestions")
          request(uri => Post(uri.withPath(Path(s"/exercise/$x/classification")), suggestions), config)
        })
        .collect()
    }

    Future(result match {
      case Success(_) => {
        println("Success!")
        Right((): Unit)
      }
      case Failure(e) => {
        println(s"Failure! $e")
        Left(e.toString)
      }
    })
  }

  private def suggestExercise(): Suggestion = {
    Session(
      new Date(),
      Programme,
      Seq(randomExercise().key),
      randomIntensity())
  }

  private def randomIntensity() = {
    val rand = new Random(System.currentTimeMillis())
    rand.nextDouble()
  }

  private def randomExercise() = {
    val rand = new Random(System.currentTimeMillis())
    val random_index = rand.nextInt(MuscleGroups.supportedMuscleGroups.size)
    MuscleGroups.supportedMuscleGroups(random_index)
  }
}
