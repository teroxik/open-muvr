package com.eigengo.lift.spark

import akka.actor.ActorSystem
import com.eigengo.lift.spark.JobManagerProtocol.BatchJobSubmit
import com.typesafe.config.ConfigFactory
import org.apache.log4j.{Logger, Level}
import org.apache.spark.Logging
import scala.concurrent.duration._
import scala.language.postfixOps

object Spark extends App with Logging {

  val log4jInitialized = Logger.getRootLogger.getAllAppenders.hasMoreElements
  if (!log4jInitialized) {
    logInfo("Setting log level to [WARN] for streaming example." +
      " To override add a custom log4j.properties to the classpath.")

    Logger.getRootLogger.setLevel(Level.WARN)
  }

  val system = ActorSystem("SparkJobManager")

  val config = ConfigFactory.load()
  val master = config.getString("spark.master")

  val manager = system.actorOf(JobManager.props(master, config))

  system.scheduler.schedule(0 seconds, 180 seconds)(manager ! BatchJobSubmit("Suggestions"))(system.dispatcher)
}
