package com.eigengo.lift.spark.jobs

import com.typesafe.config.{ConfigFactory, Config}
import org.apache.spark.SparkContext

object Batch {
  def apply[P, R](func: (SparkContext, P) => Either[String, R])(config: Config = ConfigFactory.empty()) = {
    ???
  }
}

trait Batch[P, R] extends App {

  /**
   * Could help to have compatibility with submit job scripts
   */
  //override def main(args: Array[String]) = execute()

  def name: String

  def execute(master: String, config: Config, params: P): Either[String, R]
}