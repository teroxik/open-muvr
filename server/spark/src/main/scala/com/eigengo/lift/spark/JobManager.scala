package com.eigengo.lift.spark

import akka.actor.{ActorLogging, Props, Actor}
import com.eigengo.lift.spark.JobManagerProtocol.{BatchJobSubmitFunction, BatchJobSubmit, StreamJobSubmit, JobManagerProtocol}
import com.eigengo.lift.spark.jobs.{Job, PrintCassandraEvents}
import com.typesafe.config.Config
import org.apache.spark.SparkContext

object JobManagerProtocol {
  sealed trait JobManagerProtocol
  case class StreamJobSubmit(job: String) extends JobManagerProtocol
  case class BatchJobSubmit(job: String) extends JobManagerProtocol

  case class BatchJobSubmitFunction[R](name: String, func: SparkContext => Either[String, R]) extends JobManagerProtocol

  sealed trait SparServiceResponse
  case class Analytics(data: String) extends SparServiceResponse
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
      case BatchJobSubmit("PrintCassandraEvents") =>
        submit(Job[PrintCassandraEvents], 10000)
      case BatchJobSubmit("SimplePrintJob") =>
        submit[String]("SimplePrintJob", (sc: SparkContext) => {
          sc.parallelize(0 to 100).foreach(println)
          Right("")
        })
      case x @ _ => log.warning(s"Not a job $x")
    }

    case x @ _ => log.warning(s"Unknown request $x")
  }
}
