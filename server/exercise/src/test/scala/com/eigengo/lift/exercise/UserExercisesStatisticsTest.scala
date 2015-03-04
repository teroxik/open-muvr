package com.eigengo.lift.exercise

import java.util.Date

import org.scalatest.{Matchers, FlatSpec}

class UserExercisesStatisticsTest extends FlatSpec with Matchers {
  import UserExercisesStatistics._
  val allExercises = UserExercisesStatistics.supportedMuscleGroups.flatMap(_.exercises.map(e ⇒ Exercise(e, None, None)))

  "ExerciseStatistics" should "give examples when empty" in {
    ExerciseStatistics.empty.examples() should be(theSameElementsAs(allExercises))
  }

  "ExerciseStatistics" should "give examples when non-empty" in {
    val sessionProperties = SessionProperties(new Date(), Seq("arms"), 1.0)
    val s1 =
      ExerciseStatistics.empty
        .withNewExercise(sessionProperties, Exercise("bicep curl", Some(1.0), None))
        .withNewExercise(sessionProperties, Exercise("bicep curl", Some(0.5), None))

    println(s1.examples(sessionProperties.muscleGroupKeys))
  }

}
