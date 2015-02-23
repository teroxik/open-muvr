package com.eigengo.lift.spark.jobs.suggestions

import java.util.Date

import akka.analytics.cassandra._
import akka.persistence.PersistentRepr
import com.datastax.spark.connector._
import com.eigengo.lift.Suggestion.Session
import com.eigengo.lift.SuggestionSource.Programme
import com.eigengo.lift.spark.api.{ExerciseMarshallers, HttpClient}
import com.eigengo.lift.spark.jobs.Batch
import com.eigengo.lift.{MuscleGroups, Suggestion, Suggestions}
import com.typesafe.config.Config
import org.apache.spark.{SparkConf, SparkContext}
import spray.client.pipelining._
import spray.http.Uri.Path

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.math.BigDecimal.RoundingMode
import scala.util._

/**
 * Spark job suggesting exercises based on various parameters
 * Reads history from cassandra, uses trainers hints and programmes
 *
 * Currently a test class only suggesting random exercises
 */
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

    val result = Try {
      sc.cassandraTable("akka", "messages")
        .select("processor_id", "partition_nr", "sequence_nr", "message")
        .as(journalKeyEventPair)
        .map(_._1.persistenceId)
        .filter(x => x.startsWith("user-profile-") && !x.contains("processor"))
        .map(_.drop(13))
        .distinct()
        .collect()
        .map(x => {
          //TODO: This is run in the driver program.
          //TODO: Running at workers results in serializationexception. Can be fixed?
          val rand = new Random(System.currentTimeMillis())
          val suggestions = suggestExercises(rand)

          request(uri => Post(uri.withPath(Path(s"/exercise/$x/classification")), suggestions), config)
        })
    }

    //TODO: Refactor error handling
    result match {
      case Success(f) => {
        Future.sequence(f.toList).map{a =>
          val failed = a.filter(_.isLeft)
          if(failed.isEmpty) Right((): Unit) else Left(failed.mkString(","))
        }
      }
      case Failure(e) => Future(Left(e.toString))
    }
  }

  private def suggestExercises(r: Random): Suggestions = {
    val now = new Date()

    Suggestions(
      List(
        suggestExercise(r, now),
        suggestExercise(r, new Date(now.getTime + (3600000 * 24))),
        suggestExercise(r, new Date(now.getTime + (3600000 * 48)))))
  }

  private def suggestExercise(r: Random, date: Date): Suggestion = {
    Session(
      date,
      Programme,
      Seq(randomExercise(r).key),
      randomIntensity(r))
  }

  private def randomIntensity(r: Random) = BigDecimal(r.nextDouble()).setScale(2, RoundingMode.HALF_UP).toDouble

  private def randomExercise(r: Random) =
    MuscleGroups.supportedMuscleGroups(r.nextInt(MuscleGroups.supportedMuscleGroups.size))
}
