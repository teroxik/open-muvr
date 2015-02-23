package com.eigengo.lift.exercise.classifiers

import akka.actor.{Props, ActorRef, Actor}
import com.eigengo.lift.exercise.classifiers.model.{EmptyExerciseModel, RandomExerciseModel}
import com.eigengo.lift.exercise.{SessionProperties, UserExercisesClassifier}

trait ExerciseModelChecking {
  this: Actor =>

  protected var classifier: Option[ActorRef] = None

  def registerModelChecking(sessionProps: SessionProperties): Unit = {
    classifier.foreach(context.stop)
    // TODO: replacing random model with empty model for data collection
    //classifier = Some(context.actorOf(UserExercisesClassifier.props(sessionProps, Props(new RandomExerciseModel(sessionProps)))))
    classifier = Some(context.actorOf(UserExercisesClassifier.props(sessionProps, Props(new EmptyExerciseModel(sessionProps)))))
  }
  
  def unregisterModelChecking(): Unit = {
    classifier.foreach(context.stop)
    classifier = None
  }
  
}
