package com.eigengo.lift.spark.jobs

import org.apache.spark.SparkContext

trait BatchFunction[P, R] extends ((SparkContext, P) => R)