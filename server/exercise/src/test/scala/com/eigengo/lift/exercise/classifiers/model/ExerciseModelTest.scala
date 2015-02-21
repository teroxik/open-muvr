package com.eigengo.lift.exercise.classifiers

package model

import akka.actor.{ActorRef, ActorSystem, ActorLogging}
import akka.stream.{ActorFlowMaterializer, ActorFlowMaterializerSettings}
import akka.stream.scaladsl._
import akka.testkit.{TestKit, TestProbe, TestActorRef}
import com.eigengo.lift.exercise.UserExercises.ModelMetadata
import com.eigengo.lift.exercise.UserExercisesClassifier.{ClassifiedExercise, NoExercise, Tap}
import com.eigengo.lift.exercise._
import com.eigengo.lift.exercise.classifiers.model.provers.CVC4
import com.eigengo.lift.exercise.classifiers.workflows.ClassificationAssertions
import com.typesafe.config.ConfigFactory
import java.text.SimpleDateFormat
import org.scalacheck.Gen
import org.scalacheck.Gen._
import org.scalatest._
import org.scalatest.prop._
import scala.concurrent.{ExecutionContext, Future}

class ExerciseModelTest
  extends TestKit(ActorSystem("TestSystem", ConfigFactory.load("test.conf").withFallback(ConfigFactory.load("classification.conf"))))
  with PropSpecLike
  with PropertyChecks
  with Matchers
  with ExerciseGenerators
  with ModelGenerators {

  import ClassificationAssertions._
  import ExerciseModel._

  val settings = ActorFlowMaterializerSettings(system).withInputBuffer(initialSize = 1, maxSize = 1)

  implicit val materializer = ActorFlowMaterializer(settings)

  val BindToSensorsGen: Gen[BindToSensors] = for {
    wrist <- Gen.containerOf[Set, Fact](FactGen)
    waist <- Gen.containerOf[Set, Fact](FactGen)
    foot <- Gen.containerOf[Set, Fact](FactGen)
    chest <- Gen.containerOf[Set, Fact](FactGen)
    unknown <- Gen.containerOf[Set, Fact](FactGen)
    value <- SensorNetValueGen
  } yield BindToSensors(
      wrist,
      waist,
      foot,
      chest,
      unknown,
      value
    )

  property("meet(complement(x), complement(y)) == complement(join(x, y))") {
    forAll(QueryValueGen, QueryValueGen) { (value1: QueryValue, value2: QueryValue) =>
      meet(complement(value1), complement(value2)) === complement(join(value1, value2))
    }
  }

  property("complement(complement(x)) == x") {
    forAll(QueryValueGen) { (value: QueryValue) =>
      complement(complement(value)) === value
    }
  }

  property("meet(x, y) == meet(y, x)") {
    forAll(QueryValueGen, QueryValueGen) { (value1: QueryValue, value2: QueryValue) =>
      meet(value1, value2) === meet(value2, value1)
    }
  }

  property("join(x, y) == join(y, x)") {
    forAll(QueryValueGen, QueryValueGen) { (value1: QueryValue, value2: QueryValue) =>
      join(value1, value2) === join(value2, value1)
    }
  }

  property("meet(x, meet(y, z)) == meet(meet(x, y), z)") {
    forAll(QueryValueGen, QueryValueGen, QueryValueGen) { (value1: QueryValue, value2: QueryValue, value3: QueryValue) =>
      meet(value1, meet(value2, value3)) === meet(meet(value1, value2), value3)
    }
  }

  property("join(x, join(y, z)) == join(join(x, y), z)") {
    forAll(QueryValueGen, QueryValueGen, QueryValueGen) { (value1: QueryValue, value2: QueryValue, value3: QueryValue) =>
      join(value1, join(value2, value3)) === join(join(value1, value2), value3)
    }
  }

  property("join(x, meet(y, z)) == meet(join(x, y), join(x, z))") {
    forAll(QueryValueGen, QueryValueGen, QueryValueGen) { (value1: QueryValue, value2: QueryValue, value3: QueryValue) =>
      join(value1, meet(value2, value3)) === meet(join(value1, value2), join(value1, value3))
    }
  }

  property("meet(x, join(y, z)) == join(meet(x, y), meet(x, z))") {
    forAll(QueryValueGen, QueryValueGen, QueryValueGen) { (value1: QueryValue, value2: QueryValue, value3: QueryValue) =>
      meet(value1, join(value2, value3)) === join(meet(value1, value2), meet(value1, value3))
    }
  }

  property("join(x, meet(x, y)) == x") {
    forAll(QueryValueGen, QueryValueGen) { (value1: QueryValue, value2: QueryValue) =>
      join(value1, meet(value1, value2)) === value1
    }
  }

  property("meet(x, join(x, y)) == x") {
    forAll(QueryValueGen, QueryValueGen) { (value1: QueryValue, value2: QueryValue) =>
      meet(value1, join(value1, value2)) === value1
    }
  }

  property("meet(x, x) == x") {
    forAll(QueryValueGen) { (value: QueryValue) =>
      meet(value, value) === value
    }
  }

  property("join(x, x) == x") {
    forAll(QueryValueGen) { (value: QueryValue) =>
      join(value, value) === value
    }
  }

  property("not(not(x)) == x") {
    forAll(QueryGen()) { (query: Query) =>
      ExerciseModel.not(ExerciseModel.not(query)) === query
    }
  }

  property("ExerciseModel should correctly 'slice up' SensorNet messages into SensorValue events") {
    val rate = system.settings.config.getInt("classification.frequency")
    val modelProbe = TestProbe()
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val startDate = dateFormat.parse("1970-01-01")
    val sessionProps = SessionProperties(startDate, Seq("Legs"), 1.0)
    implicit val prover = new SMTInterface {
      def simplify(query: Query)(implicit ec: ExecutionContext) = Future(query)
      def satisfiable(query: Query)(implicit ec: ExecutionContext) = Future(true)
      def valid(query: Query)(implicit ec: ExecutionContext) = Future(true)
    }
    val model = TestActorRef(new ExerciseModel("test", sessionProps) with ActorLogging {
      val workflow = Flow[SensorNetValue].map(snv => new BindToSensors(Set(), Set(), Set(), Set(), Set(), snv))
      def evaluateQuery(formula: Query)(current: BindToSensors, lastState: Boolean) = StableValue(result = true)
      def makeDecision(query: Query, value: QueryValue) = Tap
      override def aroundReceive(receive: Receive, msg: Any) = msg match {
        case value: SensorNetValue =>
          modelProbe.ref ! value

        case _ =>
          super.aroundReceive(receive, msg)
      }
    })

    forAll(MultiSensorNetGen(30)) { (rawEvent: SensorNet) =>
      val event = SensorNet(rawEvent.toMap.mapValues(_.map(evt => new SensorData { val samplingRate = rate; val values = evt.values })))

      model ! event

      val msgs = modelProbe.receiveN(event.wrist.head.values.length).asInstanceOf[Vector[SensorNetValue]].toList
      for (sensor <- Sensor.sourceLocations) {
        val numberOfPoints = rawEvent.toMap(sensor).length

        for (point <- 0 until numberOfPoints) {
          assert(msgs.map(_.toMap(sensor)(point)) == event.toMap(sensor)(point).values)
        }
      }
    }
  }

  property("ExerciseModel should generate no decisions if it watches no queries") {
    val rate = system.settings.config.getInt("classification.frequency")
    val senderProbe = TestProbe()
    val modelProbe = TestProbe()
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val startDate = dateFormat.parse("1970-01-01")
    val sessionProps = SessionProperties(startDate, Seq("Legs"), 1.0)
    implicit val prover = new SMTInterface {
      def simplify(query: Query)(implicit ec: ExecutionContext) = Future(query)
      def satisfiable(query: Query)(implicit ec: ExecutionContext) = Future(true)
      def valid(query: Query)(implicit ec: ExecutionContext) = Future(true)
    }
    val model = TestActorRef(new ExerciseModel("test", sessionProps) with ActorLogging {
      val workflow = Flow[SensorNetValue].map(snv => new BindToSensors(Set(), Set(), Set(), Set(), Set(), snv))
      def evaluateQuery(formula: Query)(current: BindToSensors, lastState: Boolean) = StableValue(result = true)
      def makeDecision(query: Query, value: QueryValue) = {
        modelProbe.ref ! (query, value)
        Tap
      }
    })

    // As a sliding window of size 2 is used, we need to submit at least 2 events to the model!
    forAll(SensorNetValueGen, SensorNetValueGen) { (event1: SensorNetValue, event2: SensorNetValue) =>
      model.underlyingActor.buffer = Vector.empty[(SensorNetValue, ActorRef)]

      model.tell(event1, senderProbe.ref)
      model.tell(event2, senderProbe.ref)

      senderProbe.expectNoMsg()
      modelProbe.expectNoMsg()
    }
  }

  property("ExerciseModel should generate single decisions if it watches a single query") {
    val rate = system.settings.config.getInt("classification.frequency")
    val senderProbe = TestProbe()
    val modelProbe = TestProbe()
    val example = Formula(Assert(Gesture("example", 0.9876), SensorDataSourceLocationAny))
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val startDate = dateFormat.parse("1970-01-01")
    val sessionProps = SessionProperties(startDate, Seq("Legs"), 1.0)
    implicit val prover = new SMTInterface {
      def simplify(query: Query)(implicit ec: ExecutionContext) = Future(query)
      def satisfiable(query: Query)(implicit ec: ExecutionContext) = Future(true)
      def valid(query: Query)(implicit ec: ExecutionContext) = Future(true)
    }
    val model = TestActorRef(new ExerciseModel("test", sessionProps, Set(example)) with ActorLogging {
      val workflow = Flow[SensorNetValue].map(snv => new BindToSensors(Set(), Set(), Set(), Set(), Set(), snv))
      def evaluateQuery(formula: Query)(current: BindToSensors, lastState: Boolean) = StableValue(result = true)
      def makeDecision(query: Query, value: QueryValue) = {
        modelProbe.ref ! (query, value)
        Tap
      }
    })

    // As a sliding window of size 2 is used, we need to submit at least 2 events to the model!
    forAll(SensorNetValueGen, SensorNetValueGen) { (event1: SensorNetValue, event2: SensorNetValue) =>
      model.underlyingActor.buffer = Vector.empty[(SensorNetValue, ActorRef)]

      model.tell(event1, senderProbe.ref)
      model.tell(event2, senderProbe.ref)

      senderProbe.expectMsg(Tap)
      val result = modelProbe.expectMsgType[(Query, QueryValue)]
      result === (example, StableValue(result = true))
    }
  }

  property("ExerciseModel should generate multiple decisions if it watches multiple queries") {
    val rate = system.settings.config.getInt("classification.frequency")
    val senderProbe = TestProbe()
    val modelProbe = TestProbe()
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val startDate = dateFormat.parse("1970-01-01")
    val sessionProps = SessionProperties(startDate, Seq("Legs"), 1.0)
    val example1 = Formula(Assert(Gesture("example1", 0.9876), SensorDataSourceLocationAny))
    val example2 = Formula(Assert(Gesture("example2", 0.5432), SensorDataSourceLocationAny))
    implicit val prover = new SMTInterface {
      def simplify(query: Query)(implicit ec: ExecutionContext) = Future(query)
      def satisfiable(query: Query)(implicit ec: ExecutionContext) = Future(true)
      def valid(query: Query)(implicit ec: ExecutionContext) = Future(true)
    }
    val model = TestActorRef(new ExerciseModel("test", sessionProps, Set(example1, example2)) with ActorLogging {
      val workflow = Flow[SensorNetValue].map(snv => new BindToSensors(Set(), Set(), Set(), Set(), Set(), snv))
      def evaluateQuery(formula: Query)(current: BindToSensors, lastState: Boolean) = StableValue(result = true)
      def makeDecision(query: Query, value: QueryValue) = {
        modelProbe.ref ! (query, value)
        Tap
      }
    })

    // As a sliding window of size 2 is used, we need to submit at least 2 events to the model!
    forAll(SensorNetValueGen, SensorNetValueGen) { (event1: SensorNetValue, event2: SensorNetValue) =>
      model.underlyingActor.buffer = Vector.empty[(SensorNetValue, ActorRef)]

      model.tell(event1, senderProbe.ref)
      model.tell(event2, senderProbe.ref)

      // As we're watching multiple queries, we expect a proportionate number of responses
      senderProbe.expectMsg(Tap)
      senderProbe.expectMsg(Tap)
      val result = modelProbe.receiveN(2).asInstanceOf[Vector[(Query, QueryValue)]].toSet
      result === Set((example1, StableValue(result = true)), (example2, StableValue(result = true)))
    }
  }

  // At all points in time, the wrist is tapped with 80% probability
  property("[true *] (End || tap@wrist >= 0.8)") {
    val watchQuery =
      All(
        Repeat(
          AssertFact(True)
        ),
        Or(
          End,
          Formula(Assert(Gesture("tap", 0.8), SensorDataSourceLocationWrist))
        )
      )

    val traceSize = 20
    val metadata = ModelMetadata(42)
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val startDate = dateFormat.parse("1970-01-01")
    val sessionProps = SessionProperties(startDate, Seq("Legs"), 1.0)
    implicit val cvc4 = new CVC4(system.settings.config)

    forAll(listOfN(traceSize, SensorNetValueGen)) { (events: List[SensorNetValue]) =>
      // Protect against shrinking during test failures
      whenever(events.length == traceSize) {
        val senderProbe = TestProbe()
        val model = TestActorRef(new ExerciseModel("test", sessionProps, Set(watchQuery)) with StandardEvaluation with ActorLogging {
          // Simulate constantly detecting a tap event on the wrist
          val workflow = Flow[SensorNetValue].map(snv => new BindToSensors(Set(Gesture("tap", 0.8)), Set(), Set(), Set(), Set(), snv))
          // Tap instance of ClassifiedExercise encodes current evaluation state
          def makeDecision(query: Query, value: QueryValue) = value match {
            case StableValue(true) =>
              Tap
            case _ =>
              NoExercise(metadata)
          }
        })

        for (evt <- events) {
          model.tell(evt, senderProbe.ref)
        }
        model.tell('Stop, senderProbe.ref)

        assert(senderProbe.receiveN(events.size).forall(_.isInstanceOf[Tap.type]))
      }
    }
  }

  // At all points in time, we tap the wrist (with 80% probability) and then have a (chest measured) heart rate above 180
  property("[true *; (tap@wrist >= 0.8)] (End || (heartrate@chest >= 180))") {
    case class Heartrate(rate: Int) extends GroundFact {
      def toString(sensor: SensorDataSourceLocation) = s"heartrate@$sensor($rate)"
    }

    val watchQuery =
      All(
        Sequence(
          Repeat(
            AssertFact(True)
          ),
          AssertFact(Assert(Gesture("tap", 0.8), SensorDataSourceLocationWrist))
        ),
        Or(
          End,
          Formula(Assert(Heartrate(180), SensorDataSourceLocationChest))
        )
      )

    val traceSize = 20
    val metadata = ModelMetadata(42)
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val startDate = dateFormat.parse("1970-01-01")
    val sessionProps = SessionProperties(startDate, Seq("Legs"), 1.0)
    implicit val cvc4 = new CVC4(system.settings.config)

    forAll(listOfN(traceSize, SensorNetValueGen)) { (events: List[SensorNetValue]) =>
      val senderProbe = TestProbe()
      val model = TestActorRef(new ExerciseModel("test", sessionProps, Set(watchQuery)) with StandardEvaluation with ActorLogging {
        var tap: Boolean = true
        // Simulate constantly detecting a wrist tap event and having a high heart rate
        val workflow = Flow[SensorNetValue].map(snv => new BindToSensors(Set(Gesture("tap", 0.8)), Set(), Set(), Set(Heartrate(180)), Set(), snv))
        // Tap instance of ClassifiedExercise encodes current evaluation state
        def makeDecision(query: Query, value: QueryValue) = value match {
          case StableValue(true) =>
            Tap
          case _ =>
            NoExercise(metadata)
        }
      })

      // Protect against shrinking during test failures
      whenever(events.length == traceSize) {
        for (evt <- events) {
          model.tell(evt, senderProbe.ref)
        }
        model.tell('Stop, senderProbe.ref)

        assert(senderProbe.receiveN(events.size).forall(_.isInstanceOf[Tap.type]))
      }
    }
  }

  // It is always the case that, after high intensity running, medium intensity running and low intensity running (with potential rests), heart rate will be above 180 bps
  property("[true *; running@any('high'); true *; running@any('medium'); true *; running@any('low')] (End || (heartrate@chest >= 180))") {
    sealed trait RunningIntensity
    case object High extends RunningIntensity {
      override def toString = "high"
    }
    case object Medium extends RunningIntensity {
      override def toString = "medium"
    }
    case object Low extends RunningIntensity {
      override def toString = "low"
    }
    case class Running(intensity: RunningIntensity) extends GroundFact {
      def toString(sensor: SensorDataSourceLocation) = s"running@$sensor($intensity)"
    }
    case class Heartrate(rate: Int) extends GroundFact {
      def toString(sensor: SensorDataSourceLocation) = s"heartrate@$sensor($rate)"
    }

    val watchQuery =
      All(
        Sequence(
          Repeat(
            AssertFact(True)
          ),
          AssertFact(Assert(Running(High), SensorDataSourceLocationAny)),
          Repeat(
            AssertFact(True)
          ),
          AssertFact(Assert(Running(Medium), SensorDataSourceLocationAny)),
          Repeat(
            AssertFact(True)
          ),
          AssertFact(Assert(Running(Low), SensorDataSourceLocationAny))
        ),
        Or(
          End,
          Formula(Assert(Heartrate(180), SensorDataSourceLocationChest))
        )
      )

    val traceSize = 20
    val metadata = ModelMetadata(42)
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val startDate = dateFormat.parse("1970-01-01")
    val sessionProps = SessionProperties(startDate, Seq("Legs"), 1.0)
    val runningGen: Gen[Running] = frequency(
      1 -> Gen.const(Running(High)),
      1 -> Gen.const(Running(Medium)),
      1 -> Gen.const(Running(Low))
    )
    implicit val cvc4 = new CVC4(system.settings.config)

    val events = listOfN(traceSize, SensorNetValueGen).sample.get

    forAll(listOfN(traceSize, SensorNetValueGen)) { (events: List[SensorNetValue]) =>
      val senderProbe = TestProbe()
      val model = TestActorRef(new ExerciseModel("test", sessionProps, Set(watchQuery)) with StandardEvaluation with ActorLogging {
        var tap: Boolean = true
        // Simulate constantly having a high heart rate and random running intensity
        val workflow = Flow[SensorNetValue].map { snv =>
          new BindToSensors(Set(), Set(), Set(), Set(Heartrate(180)), runningGen.sample.toSet, snv)
        }
        // Tap instance of ClassifiedExercise encodes current evaluation state
        def makeDecision(query: Query, value: QueryValue) = value match {
          case StableValue(true) =>
            Tap
          case _ =>
            NoExercise(metadata)
        }
      })

      // Protect against shrinking during test failures
      whenever(events.length == traceSize) {
        for (evt <- events) {
          model.tell(evt, senderProbe.ref)
        }
        model.tell('Stop, senderProbe.ref)

        assert(senderProbe.receiveN(events.size).forall(_.isInstanceOf[Tap.type]))
      }
    }
  }

}
