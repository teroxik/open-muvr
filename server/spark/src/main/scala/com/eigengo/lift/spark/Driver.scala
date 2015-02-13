package com.eigengo.lift.spark

import com.eigengo.lift.spark.jobs.Batch
import com.typesafe.config.Config
import org.apache.log4j.Logger
import org.apache.spark.{SparkConf, SparkContext}

import scala.reflect.ClassTag

trait Driver {

  def config: Config

  def master: String

  private val logger = Logger.getLogger(classOf[Driver])

  def submit[P, R](job: Batch[P, R], jobParam: P): Either[String, R] = {

    logger.info(s"Executing job ${job.name} on master $master")
    val result = job.execute(master, config, jobParam)
    logger.info(s"Job ${job.name} finished with result $result")

    result
  }

  def submit[R](name: String, job: SparkContext => Either[String, R], additionalConfig: Map[String, String] = Map()) = {
    val conf = new SparkConf()
      .setAppName(name)
      .setMaster(master)

    additionalConfig.foreach(x => conf.set(x._1, x._2))

    val sc = new SparkContext(conf)

    logger.info(s"Executing job ${name} on master $master")
    val result = job(sc)
    logger.info(s"Job ${name} finished with result $result")

    sc.stop()
    result
  }

  def submit[T](job: Stream[T]): Either[String, T] = ???
}