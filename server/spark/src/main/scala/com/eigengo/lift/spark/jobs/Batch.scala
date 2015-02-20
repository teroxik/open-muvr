package com.eigengo.lift.spark.jobs

import com.typesafe.config.Config
import org.apache.spark.{SparkConf, SparkContext}

trait Batch[P, R] extends App {

  /**
   * Could help to have compatibility with submit job scripts
   */
  //override def main(args: Array[String]) = submit[P, R](this, defaultParams(args.asInstanceOf))

  def defaultParams(args: Array[String]): P

  def name: String

  def additionalConfig: (Config, SparkConf) => SparkConf = (x, y) => y

  def execute(sc: SparkContext, config: Config, params: P): Either[String, R]
}