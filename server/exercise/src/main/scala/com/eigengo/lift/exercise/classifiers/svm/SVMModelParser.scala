package com.eigengo.lift.exercise.classifiers.svm

import breeze.linalg.{DenseMatrix, DenseVector}
import com.typesafe.config.Config
import org.parboiled2._
import scala.io.Source
import scala.language.implicitConversions
import scalaz.{DisjunctionFunctions, \/, -\/}

private[svm] trait ParserUtils extends StringBuilding {
  this: Parser =>

  import CharPredicate.{Alpha, AlphaNum, Digit, Digit19}

  implicit def wsStr(s: String): Rule0 = rule {
    str(s) ~ WS
  }

  def WS: Rule0 = rule {
    zeroOrMore(anyOf(" \t"))
  }

  def NL: Rule0 = rule {
    optional('\r') ~ '\n'
  }

  def Decimal: Rule1[Double] = rule {
    capture(optional(anyOf("+-")) ~ oneOrMore(Digit) ~ optional('.' ~ oneOrMore(Digit)) ~ optional(ignoreCase('e') ~ optional(anyOf("+-")) ~ oneOrMore(Digit))) ~> ((w: String) => w.toDouble)
  }
  
  def Integer: Rule1[Int] = rule {
    capture(optional(anyOf("+-")) ~ (Digit19 ~ zeroOrMore(Digit) | Digit)) ~> ((w: String) => w.toInt)
  }
  
  def Identifier: Rule1[String] = rule {
    capture((Alpha | '_') ~ zeroOrMore(AlphaNum | '_'))
  }

}

private[svm] object LibSVMParser {

  // (internal) AST for libsvm file
  case class HeaderEntry(key: String, value: String)
  case class Header(values: Seq[HeaderEntry])
  case class SupportVectorIndex(index: Int, value: Double)
  case class SupportVectorEntry(label: Double, values: Seq[SupportVectorIndex])
  case class SupportVector(values: Seq[SupportVectorEntry])

}

/**
 * Provides a parser suitable for reading libsvm files generated by R. We bake in the assumption that we are parsing a
 * libsvm model of type C-classification and that uses a radial basis function kernel.
 */
private[svm] class LibSVMParser(val input: ParserInput) extends Parser with ParserUtils {

  import CharPredicate.AlphaNum
  import LibSVMParser._
  import SVMClassifier._

  // PEG rules
  def ValueRule: Rule1[String] = rule {
    (Decimal | Integer | Identifier) ~> ((w: Any) => w.toString)
  }

  def HeaderEntryRule: Rule1[HeaderEntry] = rule {
    capture(oneOrMore(AlphaNum | '_')) ~ WS ~ ValueRule ~> HeaderEntry
  }

  def HeaderRule: Rule1[Header] = rule {
    zeroOrMore(HeaderEntryRule).separatedBy(WS ~ NL) ~> Header
  }

  def SupportVectorIndexRule: Rule1[SupportVectorIndex] = rule {
    (Integer ~ ':' ~ Decimal) ~> SupportVectorIndex
  }

  // NOTE: we intentionally ignore the fact that labels may (in general) be comma separated
  def SupportVectorEntryRule: Rule1[SupportVectorEntry] = rule {
    (Decimal ~ WS ~ zeroOrMore(SupportVectorIndexRule).separatedBy(WS)) ~> SupportVectorEntry
  }

  def SupportVectorRule: Rule1[SupportVector] = rule {
    zeroOrMore(SupportVectorEntryRule).separatedBy(WS ~ NL) ~> SupportVector
  }

  // root parsing rule
  def parse: Rule1[SVMModel] = rule {
    (HeaderRule ~> ((hdr: Header) => {
      val hdrList = hdr.values
      val kv = Map(hdrList.map { case HeaderEntry(key, value) => (key, value) }: _*)

      // Sanity checking of the parsed libsvm model description - any deviation is a parse error!
      test(kv.contains("svm_type") && kv("svm_type") == "c_svc") ~
      test(kv.contains("kernel_type") && kv("kernel_type") == "rbf") ~
      test(kv.contains("nr_class") && kv("nr_class") == 2.toString) ~
      test(kv.contains("gamma")) ~
      test(kv.contains("rho")) ~
      test(kv.contains("probA")) ~
      test(kv.contains("probB")) ~
      push(hdr)
    })) ~
    str("SV") ~ NL ~
    (SupportVectorRule ~> ((sv: SupportVector) => {
      val svList = sv.values
      // Sanity checking of the parsed libsvm model description - any deviation is a parse error!
      test(svList.nonEmpty) ~
      push(sv)
    })) ~
    zeroOrMore(NL) ~
    EOI ~> ((hdr: Header, sv: SupportVector) => {
      val hdrList = hdr.values
      val svList = sv.values
      val kv = Map(hdrList.map { case HeaderEntry(key, value) => (key, value) }: _*)
      val svSize = svList.map { case SupportVectorEntry(_, values) => values.map(_.index).max }.max

      // Sanity checking of the parsed libsvm model description - any deviation is a parse error!
      test(kv.contains("total_sv") && kv("total_sv") == svList.length.toString) ~
      push(SVMModel(
        nSV = kv("total_sv").toInt,
        // By default, missing support vector indexes are taken to have a value of zero
        SV = DenseMatrix.tabulate(kv("total_sv").toInt, svSize) { case (r, c) => svList(r).values.find(_.index == c).map(_.value).getOrElse(0) },
        gamma = kv("gamma").toDouble,
        coefs = DenseVector(svList.map(_.label): _*),
        rho = kv("rho").toDouble,
        probA = kv("probA").toDouble,
        probB = kv("probB").toDouble,
        scaled = None // Will be defined by the calling parsing context
      ))
    })
  }

}

/**
 * Provides a parser suitable for reading scale files generated by R.
 */
private[svm] class SVMScaleParser(val input: ParserInput) extends Parser with ParserUtils {

  import SVMClassifier._

  // (internal) AST for scale file
  case class ScaledData(x: Double, center: Double)

  // PEG rules
  def ScaledDataRule: Rule1[ScaledData] = rule {
    Decimal ~ WS ~ Decimal ~> ScaledData
  }

  // root parsing rule
  def parse: Rule1[SVMScale] = rule {
    zeroOrMore(ScaledDataRule).separatedBy(WS ~ NL) ~ zeroOrMore(WS ~ NL) ~ WS ~ EOI ~> ( (data: Seq[ScaledData]) =>
      SVMScale(DenseVector(data.map(_.x): _*), DenseVector(data.map(_.center): _*))
    )
  }

}

/**
 * Parses R generated libSVM and scale files to build an SVM model. R files making up our model are assumed to be on the
 * classes resource path.
 *
 * @param name name of libSVM model we are to parse
 */
class SVMModelParser(name: String)(implicit config: Config) extends DisjunctionFunctions {

  import Parser.DeliveryScheme.Either
  import SVMClassifier._

  val modelPath = config.getString("classification.model.path")
  val modelName = config.getString(s"classification.gesture.$name.model")

  def model: \/[String, SVMModel] = {
    Option(getClass.getResource(s"$modelPath/$modelName.libsvm")).map { libSVMFile =>
      Option(getClass.getResource(s"$modelPath/$modelName.scale")).map { scaledFile =>
        for {
          libsvm <- fromEither(new LibSVMParser(Source.fromURL(libSVMFile, "UTF-8").mkString).parse.run()).leftMap(_.getMessage)
          scale  <- fromEither(new SVMScaleParser(Source.fromURL(scaledFile, "UTF-8").mkString).parse.run()).leftMap(_.getMessage)
        } yield libsvm.copy(scaled = Some(scale))
      }.getOrElse(-\/(s"$modelPath/$modelName.scale does not exist on the class path!"))
    }.getOrElse(-\/(s"$modelPath/$modelName.libsvm does not exist on the class path!"))
  }

}
