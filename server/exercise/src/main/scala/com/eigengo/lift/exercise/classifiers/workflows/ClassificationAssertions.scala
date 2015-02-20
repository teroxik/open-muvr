package com.eigengo.lift.exercise.classifiers.workflows

import com.eigengo.lift.exercise._

object ClassificationAssertions {

  /**
   * Facts that may hold of sensor data.
   */
  sealed trait Fact

  case class Neg(fact: GroundFact) extends Fact

  /**
   * Ground facts logically model predicates regarding actual sensor data values
   */
  trait GroundFact extends Fact
  /**
   * Named gesture matches with probability >= `matchProbability`
   */
  case class Gesture(name: String, matchProbability: Double) extends GroundFact

  /**
   * Bind inferred (e.g. machine learnt) assertions to sensors in a network of sensorse.
   *
   * @param wrist   facts true of this location
   * @param waist   facts true of this location
   * @param foot    facts true of this location
   * @param chest   facts true of this location
   * @param unknown facts true of this location
   * @param value   raw sensor network data that assertion holds for
   */
  case class BindToSensors(wrist: Set[Fact], waist: Set[Fact], foot: Set[Fact], chest: Set[Fact], unknown: Set[Fact], value: SensorNetValue) {
    val toMap = Map[SensorDataSourceLocation, Set[Fact]](
      SensorDataSourceLocationWrist -> wrist,
      SensorDataSourceLocationWaist -> waist,
      SensorDataSourceLocationFoot -> foot,
      SensorDataSourceLocationChest -> chest,
      SensorDataSourceLocationAny -> unknown
    )
  }

  object BindToSensors {
    def apply(sensorMap: Map[SensorDataSourceLocation, Set[Fact]], value: SensorNetValue) =
      new BindToSensors(
        sensorMap(SensorDataSourceLocationWrist),
        sensorMap(SensorDataSourceLocationWaist),
        sensorMap(SensorDataSourceLocationFoot),
        sensorMap(SensorDataSourceLocationChest),
        sensorMap(SensorDataSourceLocationAny),
        value
      )
  }

}
