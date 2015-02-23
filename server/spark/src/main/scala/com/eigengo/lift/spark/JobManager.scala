package com.eigengo.lift.spark

import akka.actor.{ActorLogging, Props, Actor}
import com.eigengo.lift.spark.JobManagerProtocol.{BatchJobSubmitFunction, BatchJobSubmit, StreamJobSubmit, JobManagerProtocol}
import com.eigengo.lift.spark.jobs.Job
import com.eigengo.lift.spark.jobs.suggestions.TestSuggestionsJob
import com.typesafe.config.Config
import org.apache.spark.SparkContext
import akka.pattern.{PipeToSupport}

import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future

/**
 * Protocol for communication with JobManager
 */
object JobManagerProtocol {

  /**
   * Job submission protocol
   */
  sealed trait JobManagerProtocol

  /**
   * Submit a Streaming job
   * @param job job name
   */
  case class StreamJobSubmit(job: String) extends JobManagerProtocol

  /**
   * Submit a Batch job
   * @param job job name
   */
  case class BatchJobSubmit(job: String) extends JobManagerProtocol

  /**
   * Submit a Batch job using a function
   * @param name job name
   * @param func function
   * @tparam R function return type
   */
  case class BatchJobSubmitFunction[R](name: String, func: SparkContext => Future[Either[String, R]]) extends JobManagerProtocol
}

/**
 * Utility functions for JobManager
 */
object JobManager {

  /**
   * Spark service props
   * @return
   */
  def props(master: String, config: Config): Props = Props(new JobManager(master, config))
}

/**
 * JobManager receives requests for Job submission into Spark and handles them
 *
 * @param master url of spark master. Can be any Spark supported url
 * @param config configuration
 */
class JobManager(
    override val master: String,
    override val config: Config)
  extends Actor with Driver with ActorLogging with PipeToSupport {

  override def receive: Receive = {
    //TODO: Add job information
    case result: Either[_, _] => result match {
      case Right(_) => log.info("Job finished successfully")
      case Left(a) => log.warning(s"Job failed $a")
    }

    case m: JobManagerProtocol => m match {
      case StreamJobSubmit(d) =>
        log.warning(s"Stream functionality not yet supported $d!")

      case BatchJobSubmitFunction(name, func) =>
        submit(name, func)

      case BatchJobSubmit(x) => x match {
        case "Suggestions" =>
          submit(Job[TestSuggestionsJob], ()).pipeTo(self)
      }

      case x @ _ => log.warning(s"Not a job $x")
    }

    case x @ _ => log.warning(s"Unknown request $x")
  }
}
