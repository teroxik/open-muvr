package com.eigengo.lift.exercise

import java.util.Date

import org.scalatest.{Matchers, FlatSpec}

class UserExercisesStatisticsTest extends FlatSpec with Matchers {
  import UserExercisesStatistics._
  val allExercises = UserExercisesStatistics.supportedMuscleGroups.flatMap(_.exercises.map(e ⇒ Exercise(e, None, None))).sortWith { (l, r) ⇒ l.name < r.name }

  "ExerciseStatistics" should "give examples when empty" in {
    ExerciseStatistics.empty.examples() should be(allExercises)

    println(ExerciseStatistics.empty.examples(Seq("arms")))
  }

  "ExerciseStatistics" should "give examples when non-empty" in {
    val sessionProperties = SessionProperties(new Date(), Seq("arms"), 1.0)
    val s1 =
      ExerciseStatistics.empty
        .withNewExercise(sessionProperties, Exercise("bicep curl", Some(1.0), None))
        .withNewExercise(sessionProperties, Exercise("bicep curl", Some(0.5), None))

    // no user examples for 0.5 intensity
    s1.examples(sessionProperties.muscleGroupKeys, 0.5).head should be (Exercise("bicep curl", None, None))

    // for arms intensity 1.0, we have example with 0.75 (see the two exercises above)
    s1.examples(sessionProperties.muscleGroupKeys, 1.0).head should be (Exercise("bicep curl", Some(0.75), None))

    // for arms, we have the same two exercises
    s1.examples(sessionProperties.muscleGroupKeys).head should be (Exercise("bicep curl", Some(0.75), None))

    // general examples include the same two exercises
    s1.examples().head should be (Exercise("bicep curl", Some(0.75), None))
  }

}
