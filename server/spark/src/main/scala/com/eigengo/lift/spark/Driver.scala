package com.eigengo.lift.spark

import com.eigengo.lift.spark.jobs.Batch
import com.typesafe.config.Config
import org.apache.log4j.Logger
import org.apache.spark.{SparkConf, SparkContext}

import scala.concurrent.Future

/**
 * Driver submits jobs to Spack Cluster
 * Maintains connection to Cluster as SparkContext and provides that managed context to requesting jobs
 */
trait Driver {

  /**
   * Configuration
   * @return configuration
   */
  def config: Config

  /**
   * Master url. Supports any Spark supported url.
   * @return url
   */
  def master: String

  private val logger = Logger.getLogger(classOf[Driver])

  private val sc = sparkContext("Spark Driver",  (c, conf) => {
    conf.set("spark.cassandra.connection.host", c.getString("cassandra.host"))
    .set("spark.cassandra.journal.keyspace", "akka")
    .set("spark.cassandra.journal.table", "messages")
  })

  private def sparkContext(name: String, additionalConfig: (Config, SparkConf) => SparkConf) = {
    val conf = new SparkConf()
      .setAppName(name)
      .setMaster(master)

    val sc = new SparkContext(additionalConfig(config, conf))
    sc.addJar("/app/spark-assembly-1.0.0-SNAPSHOT.jar")
    sc
  }

  /**
   * Submits a job to Spark Cluster
   * @param job job to be submitted
   * @param jobParam parameters passed to the job
   * @tparam P job parameter type
   * @tparam R job return type
   * @return Left(String) in case of failure, Right(R) otherwise
   */
  def submit[P, R](job: Batch[P, R], jobParam: P): Future[Either[String, R]] = {
    //val sc = sparkContext(job.name, job.additionalConfig)

    logger.info(s"Executing job ${job.name} on master $master")
    val result = job.execute(sc, config, jobParam)
    logger.info(s"Job ${job.name} finished with result $result")

    //sc.stop()
    result
  }

  /**
   * Submits anonymous job to Spack Cluster
   * @param name name of the job
   * @param job function specifying the job
   * @param additionalConfig additional configuration the job may want to set
   * @tparam R job return type
   * @return Left(String) in case of failure, Right(R) otherwise
   */
  def submit[R](
      name: String,
      job: SparkContext => Future[Either[String, R]],
      additionalConfig: (Config, SparkConf) => SparkConf = (x, y) => y): Future[Either[String, R]] = {

    //val sc = sparkContext(name, additionalConfig)

    logger.info(s"Executing job ${name} on master $master")
    val result = job(sc)
    logger.info(s"Job ${name} finished with result $result")

    //sc.stop()
    result
  }

  /**
   * Submits a streaming job to cluster
   * @param job streaming job
   * @tparam T type of streaming job parameter
   * @return not implemented exception
   */
  def submit[T](job: Stream[T]): Either[String, T] = ???
}