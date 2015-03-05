package com.eigengo.lift.exercise.classifiers

import akka.actor.{Props, ActorRef, Actor}
import com.eigengo.lift.Exercise.RequestedClassification
import com.eigengo.lift.exercise.classifiers.model.RandomExerciseModel
import com.eigengo.lift.exercise._

trait ExerciseModelChecking {
  this: Actor =>

  protected var classifier: Option[ActorRef] = None

  def registerModelChecking(sessionProps: SessionProperties): Unit = {
    classifier.foreach(context.stop)
    classifier = sessionProps.classification match {
      case RequestedClassification.RandomClassification ⇒ Some(context.actorOf(UserExercisesClassifier.props(sessionProps, Props(new RandomExerciseModel(sessionProps)))))
      case RequestedClassification.ExplicitClassification ⇒ None
    }
  }
  
  def unregisterModelChecking(): Unit = {
    classifier.foreach(context.stop)
    classifier = None
  }
  
}
