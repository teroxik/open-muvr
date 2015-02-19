package com.eigengo.lift.spark.jobs

import com.eigengo.lift.spark.api.HttpClient
import com.typesafe.config.Config
import org.apache.spark.{SparkConf, SparkContext}
import akka.analytics.cassandra._
import spray.http.Uri.Path
import spray.client.pipelining._

import scala.util.{Success, Failure, Try}

import scala.concurrent.ExecutionContext.Implicits.global

case class PrintCassandraEvents() extends Batch[Int, Unit] with HttpClient {

  override def name: String = "PrintCassandraEvents"

  override def additionalConfig: (Config, SparkConf) => SparkConf = (c, conf) =>
    conf.set("spark.cassandra.connection.host", c.getString("cassandra.host"))
      .set("spark.cassandra.journal.keyspace", "akka")
      .set("spark.cassandra.journal.table", "messages")

  override def execute(sc: SparkContext, config: Config, params: Int): Either[String, Unit] = {
    val result = Try {
      println("CASSANDRA EVENT TABLE: ")
      sc.eventTable().cache().collect().foreach(println)
    }

    request(uri => Get(uri.withPath(Path("/exercise/musclegroups"))), config).onComplete({
      case Success(x) => println(x)
      case Failure(e) => println(e)
    })

    result match {
      case Success(_) => Right((): Unit)
      case Failure(e) => Left(e.toString)
    }
  }

  override def defaultParams(args: Array[String]): Int = 0
}