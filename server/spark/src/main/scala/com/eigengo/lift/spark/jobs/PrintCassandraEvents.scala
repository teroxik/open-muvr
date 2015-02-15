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

    config.entrySet().toArray.foreach(println)

    val sc = new SparkContext(new SparkConf()
      .setAppName(name)
      .setMaster("local[5]"))
      /*.set("spark.cassandra.connection.host", config.getString("cassandra.host"))
      .set("spark.cassandra.journal.keyspace", "akka")
      .set("spark.cassandra.journal.table", "messages")
      .set("spark.driver.host", InetAddress.getLocalHost.getHostAddress)
      .set("spark.driver.port", "9001"))*/

    sc.parallelize(1 to 1000).map(_ + 1).foreach(println)
    //sc.eventTable().cache().collect().foreach(println)

    sc.stop()

    Right((): Unit)
  }
}
