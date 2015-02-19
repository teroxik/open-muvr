package com.eigengo.lift.spark.jobs

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.apache.spark.{SparkConf, SparkContext}
import akka.analytics.cassandra._
import spray.http.{Uri, HttpResponse, HttpRequest}
import spray.client.pipelining._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Success, Failure, Try}

import scala.concurrent.ExecutionContext.Implicits.global

case class PrintCassandraEvents() extends Batch[Int, Unit] {

  override def name: String = "PrintCassandraEvents"

  implicit def actorSystem = ActorSystem("Cassandra")

  def pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  override def execute(master: String, config: Config, params: Int): Either[String, Unit] = {

    /*.set("spark.driver.host", InetAddress.getLocalHost.getHostAddress)
      .set("spark.driver.port", "9001")*/

    val restApi = Uri(s"http://${config.getString("app.rest.api")}/exercise/musclegroups").withPort(config.getInt("app.rest.port"))

    println("API URI")
    println(restApi)

    val goog = Await.result(pipeline(Get(restApi)), Duration("10 second"))

    println("API RESPONSE")
    println(goog)

    val sc = new SparkContext(new SparkConf()
      .setAppName(name)
      .setMaster(master)
      .set("spark.cassandra.connection.host", config.getString("cassandra.host"))
      .set("spark.cassandra.journal.keyspace", "akka")
      .set("spark.cassandra.journal.table", "messages"))

    sc.addJar("/app/spark-assembly-1.0.0-SNAPSHOT.jar")

    val result = Try {
      println("CASSANDRA EVENT TABLE: ")
      sc.eventTable().cache().collect().foreach(println)
    }

    sc.stop()

    result match {
      case Success(_) => Right((): Unit)
      case Failure(e) => Left(e.toString)
    }
  }
}