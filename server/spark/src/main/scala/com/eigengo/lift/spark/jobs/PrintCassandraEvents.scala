package com.eigengo.lift.spark.jobs

import akka.persistence.PersistentRepr
import com.eigengo.lift.spark.api.HttpClient
import com.typesafe.config.Config
import org.apache.spark.{SparkConf, SparkContext}
import akka.analytics.cassandra._
import com.datastax.spark.connector._
import spray.http.Uri.Path
import spray.client.pipelining._

import scala.concurrent.Future
import scala.util.{Success, Failure, Try}

import scala.concurrent.ExecutionContext.Implicits.global

case class PrintCassandraEvents() extends Batch[Int, Unit] with HttpClient {

  override def name: String = "PrintCassandraEvents"

  override def additionalConfig: (Config, SparkConf) => SparkConf = (c, conf) =>
    conf.set("spark.cassandra.connection.host", c.getString("cassandra.host"))
      .set("spark.cassandra.journal.keyspace", "akka")
      .set("spark.cassandra.journal.table", "messages")

  override def execute(sc: SparkContext, config: Config, params: Int): Future[Either[String, Unit]] = {
    val journalKeyEventPair = (persistenceId: String, partition: Long, sequenceNr: Long, message: PersistentRepr) =>
      (JournalKey(persistenceId, partition, sequenceNr), message.payload)

    val result = Try {
      //val events = sc.eventTable().collect()
      sc.cassandraTable("akka", "messages")
        .select("processor_id", "partition_nr", "sequence_nr", "message")
        .as(journalKeyEventPair)
        .collect()
        .foreach(e => println(s"EVENT $e"))
    }

    request(uri => Get(uri.withPath(Path("/exercise/musclegroups"))), config).onComplete({
      case Success(x) => println(x)
      case Failure(e) => println(e)
    })

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

  override def defaultParams(args: Array[String]): Int = 0
}