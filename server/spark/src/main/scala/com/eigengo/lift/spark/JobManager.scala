package com.eigengo.lift.spark

import akka.actor.{ActorLogging, Props, Actor}
import com.eigengo.lift.spark.JobManagerProtocol.{BatchJobSubmitFunction, BatchJobSubmit, StreamJobSubmit, JobManagerProtocol}
import com.eigengo.lift.spark.jobs.suggestions.TestSuggestionsJob
import com.eigengo.lift.spark.jobs.{Job, PrintCassandraEvents}
import com.typesafe.config.Config
import org.apache.spark.SparkContext

import scala.concurrent.Future

object JobManagerProtocol {
  sealed trait JobManagerProtocol
  case class StreamJobSubmit(job: String) extends JobManagerProtocol
  case class BatchJobSubmit(job: String) extends JobManagerProtocol
  case class BatchJobSubmitFunction[R](name: String, func: SparkContext => Future[Either[String, R]]) extends JobManagerProtocol
}

object JobManager {

  /**
   * Spark service props
   * @return
   */
  def props(master: String, config: Config): Props = Props(new JobManager(master, config))
}

class JobManager(
    override val master: String,
    override val config: Config)
  extends Actor with Driver with ActorLogging {

  override def receive: Receive = {
    case m: JobManagerProtocol => m match {
      case StreamJobSubmit(d) =>
        println(s"stream $d")

      case BatchJobSubmitFunction(name, func) =>
        submit(name, func)

      case BatchJobSubmit(x) => x match {
        case "PrintCassandraEvents" =>
          val result = submit(PrintCassandraEvents(), 10000)
          log.info(s"Job PrintCassandraEvents resulted in $result")

        case "Suggestions" =>
          val result = submit(new TestSuggestionsJob(), ())
          log.info(s"Job Suggestions resulted in $result")
      }

      case x @ _ => log.warning(s"Not a job $x")
    }

    case x @ _ => log.warning(s"Unknown request $x")
  }
}
