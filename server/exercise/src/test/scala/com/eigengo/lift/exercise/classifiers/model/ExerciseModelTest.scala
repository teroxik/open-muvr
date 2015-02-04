package com.eigengo.lift.exercise.classifiers.model

import com.eigengo.lift.exercise.classifiers.ExerciseModel
import com.eigengo.lift.exercise.classifiers.workflows.ClassificationAssertions
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.Gen._
import org.scalatest._
import org.scalatest.prop._

class ExerciseModelTest extends PropSpec with PropertyChecks with Matchers {

  import ClassificationAssertions._
  import ExerciseModel._

  val FactGen: Gen[Fact] = frequency(
    1 -> (for { name <- arbitrary[String]; matchProbability <- arbitrary[Double] } yield Gesture(name, matchProbability)),
    1 -> (for { name <- arbitrary[String]; matchProbability <- arbitrary[Double] } yield NegGesture(name, matchProbability))
  )

  val AssertionGen: Gen[Assertion] = frequency(
    1 -> FactGen.map(Predicate),
    1 -> Gen.const(True),
    1 -> Gen.const(False),
    1 -> (nonEmptyListOf(AssertionGen) suchThat (_.length >= 2)).map(qList => Conjunction(qList(0), qList(1), qList.drop(2): _*)),
    1 -> (nonEmptyListOf(AssertionGen) suchThat (_.length >= 2)).map(qList => Disjunction(qList(0), qList(1), qList.drop(2): _*))
  )

  def PathGen: Gen[Path] = frequency(
    1 -> AssertionGen.map(Assert),
    1 -> QueryGen.map(Test),
    1 -> (nonEmptyListOf(PathGen) suchThat (_.length >= 2)).map(qList => Choice(qList(0), qList(1), qList.drop(2): _*)),
    1 -> (nonEmptyListOf(PathGen) suchThat (_.length >= 2)).map(qList => Seq(qList(0), qList(1), qList.drop(2): _*)),
    1 -> PathGen.map(Repeat)
  )

  def QueryGen: Gen[Query] = frequency(
    1 -> AssertionGen.map(Formula),
    1 -> Gen.const(TT),
    1 -> Gen.const(FF),
    1 -> (for { query1 <- QueryGen; query2 <- QueryGen } yield And(query1, query2)),
    1 -> (for { query1 <- QueryGen; query2 <- QueryGen } yield Or(query1, query2)),
    1 -> (for { path <- PathGen; query <- QueryGen } yield Exists(path, query)),
    1 -> (for { path <- PathGen; query <- QueryGen } yield All(path, query))
  )

  val QueryValueGen: Gen[QueryValue] = frequency(
    1 -> arbitrary[Boolean].map(StableValue),
    1 -> arbitrary[Boolean].map(UnstableValue)
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
    forAll(QueryGen) { (query: Query) =>
      ExerciseModel.not(ExerciseModel.not(query)) === query
    }
  }

}
