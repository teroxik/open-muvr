package com.eigengo.lift.spark.jobs

import com.typesafe.config.Config
import org.apache.spark.{SparkConf, SparkContext}
import akka.analytics.cassandra._

import java.net._

case class PrintCassandraEvents() extends Batch[Int, Unit] {

  override def name: String = "PrintCassandraEvents"

  override def execute(master: String, config: Config, params: Int): Either[String, Unit] = {

    println("SPARK DRIVER HOST:")
    println(InetAddress.getLocalHost.getHostAddress)

    val sc = new SparkContext(new SparkConf()
      .setAppName(name)
      .setMaster(master)
      .set("spark.cassandra.connection.host", config.getString("cassandra.host"))
      .set("spark.cassandra.journal.keyspace", "akka")
      .set("spark.cassandra.journal.table", "messages")
      .set("spark.driver.host", InetAddress.getLocalHost.getHostAddress)
      .set("spark.driver.port", "9001"))

    sc.eventTable().cache().collect().foreach(println)

    sc.stop()

    Right((): Unit)
  }
}
