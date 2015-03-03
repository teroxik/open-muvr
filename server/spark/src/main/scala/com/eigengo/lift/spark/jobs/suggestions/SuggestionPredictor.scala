package com.eigengo.lift.spark.jobs.suggestions

import java.util.Date

import com.eigengo.lift.spark.jobs.suggestions.SuggestionPipeline.Predictor
import org.apache.spark.mllib.classification.NaiveBayes
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.rdd.RDDFunctions._
import SuggestionPipeline.Processing._
import org.apache.spark.mllib.regression.LabeledPoint

import scala.util.Random

object SuggestionPredictor {

  def historyPredictor: Predictor = { (user, events, useHistory, predictDays) =>

    val size = events.count()
    val now = new Date()

    val trainingSet = events
      .sliding(useHistory)
      .map(exercises => LabeledPoint(exercises.head, Vectors.dense(exercises.tail)))

    val model = NaiveBayes.train(trainingSet)

    var testData = events
      .zipWithIndex()
      .filter(_._2 > size - useHistory - 1)
      .collect()
      .map(_._1)

    for (i <- 0 to predictDays - 1) yield {
      testData = events
        .zipWithIndex()
        .filter(_._2 > size - useHistory - 1 + i)
        .collect()
        .map(_._1) ++ testData.takeRight(i)

      val testLabeledPoint = LabeledPoint(testData.head, Vectors.dense(testData.tail))

      (model.predict(testLabeledPoint.features), addDays(now, i + 1))
    }
  }

  def randomPredictor: Predictor = { (user, events, useHistory, predictDays) =>
    val now = new Date()
    val rand = new Random(System.currentTimeMillis())

    for (i <- 0 to predictDays - 1) yield {
      (randomExercise(rand), addDays(now, i + 1))
    }
  }

  private def addDays(date: Date, days: Int) =
    new Date(date.getTime + (3600000 * 24 * days))
}
