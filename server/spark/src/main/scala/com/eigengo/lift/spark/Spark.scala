package com.eigengo.lift.spark

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.eigengo.lift.spark.JobManagerProtocol.BatchJobSubmit
import com.typesafe.config.ConfigFactory
import org.apache.log4j.{Logger, Level}
import org.apache.spark.Logging
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Main backend Spark application
 * Runs Spark job manager and driver which on demand submits jobs to configured cluster
 */
object Spark extends App with Logging {

  val system = ActorSystem("SparkJobManager")

  val config = ConfigFactory.load()
  val master = config.getString("spark.master")

  val manager = system.actorOf(JobManager.props(master, config))

  val interval = FiniteDuration(config.getDuration("jobs.suggestions.interval", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)

  //Start suggestions job. The job is re-run every jobs.suggestions.interval milliseconds.
  system.scheduler.schedule(
    180 seconds,
    FiniteDuration(
      config.getDuration(
        "jobs.suggestions.interval",
        TimeUnit.MILLISECONDS),
        TimeUnit.MILLISECONDS
    ))(manager ! BatchJobSubmit("Suggestions"))(system.dispatcher)
}
