package com.eigengo.lift.spark.jobs

import com.typesafe.config.Config
import org.apache.spark.{SparkConf, SparkContext}

import scala.concurrent.Future

/**
 * Represents a batch job that can be executed in Spark
 * @tparam P input parameters
 * @tparam R return type
 */
trait Batch[P, R] extends App {

  /**
   * Could help to have compatibility with submit job scripts
   */
  //override def main(args: Array[String]) = submit[P, R](this, defaultParams(args.asInstanceOf))

  /**
   * Parser for default parameters when run using Spark submit script
   * @param args command line parameters
   * @return parameters as type P
   */
  def defaultParams(args: Array[String]): P

  /**
   * Name of the job
   * @return name of the job
   */
  def name: String

  /**
   * Spark config to Spark master is established automatically, but job may want to add specific config
   * @return configuration
   */
  def additionalConfig: (Config, SparkConf) => SparkConf = (x, y) => y

  /**
   * Execute a job on defined Spark Cluster using injected SparkContext
   * @param sc SparkContext representing Cluster. It is provided by the Driver
   * @param config additional configuration
   * @param params job parameters
   * @return Left(String) in case of job failure or Right(R) in case of success
   */
  def execute(sc: SparkContext, config: Config, params: P): Future[Either[String, R]]
}