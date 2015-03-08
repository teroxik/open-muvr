package com.eigengo.lift.spark.jobs

import scala.reflect.ClassTag

/**
 * Wrapper around jobs allowing simple construction of jobs
 */
object Job {

  /**
   * Contruct a Batch job
   * @tparam J type of Batch job
   * @return Batch job instance
   */
  def apply[J <: Batch[_, _]: ClassTag]() = {
    implicitly[ClassTag[J]].runtimeClass.newInstance().asInstanceOf[J]
  }
}
