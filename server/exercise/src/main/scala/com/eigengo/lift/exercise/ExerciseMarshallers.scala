package com.eigengo.lift.exercise

import java.text.SimpleDateFormat
import java.util.Date

import com.eigengo.lift.common.{CommonMarshallers, CommonPathDirectives}
import org.json4s.JsonAST._
import org.json4s.native.JsonParser
import spray.http._
import spray.httpx.marshalling.{Marshaller, ToResponseMarshaller, ToResponseMarshallingContext}
import spray.httpx.unmarshalling._
import spray.routing._
import spray.routing.directives.{MarshallingDirectives, PathDirectives}

import scala.util.Try

/**
 * Defines the marshallers for the Lift system
 */
trait ExerciseMarshallers extends MarshallingDirectives with PathDirectives with CommonPathDirectives with CommonMarshallers {

  def getDate(map: Map[String, JValue], key: String): Option[Date] = {
    map.get(key).flatMap {
      case JString(dateString) ⇒ Try {
        json4sFormats.dateFormat.parse(dateString)
      }.getOrElse(None)
      case _ ⇒ None
    }
  }

  def getStringArray(map: Map[String, JValue], key: String): Option[List[String]] = {
    map.get(key).flatMap {
      case JArray(muscles) ⇒ Some(muscles.flatMap {
        case JString(muscle) ⇒ Some(muscle)
        case _ ⇒ None})
      case _ ⇒ None
    }
  }

  def getDouble(map: Map[String, JValue], key: String): Option[Double] = {
    map.get(key).flatMap {
      case JDouble(num) ⇒ Some(num)
      case _ ⇒ None
    }
  }

  def getRequestedClassification(map: Map[String, JValue], key: String): Option[RequestedClassification] = {
    map.get(key).flatMap {
      case JString("RandomClassification") ⇒ Some(RandomClassification)
      case JString("ExplicitClassification") ⇒ Some(ExplicitClassification)
      case _ ⇒ None
    }
  }

  implicit object SessionPropertiesUnmarshaller extends FromRequestUnmarshaller[SessionProperties] {
    def apply(request: HttpRequest): Deserialized[SessionProperties] = {

      def getSessionProperties(values: Map[String, JValue]): Option[SessionProperties] = {
        (for (
          startDate <- getDate(values, "startDate");
          muscleGroupKeys <- getStringArray(values, "muscleGroupKeys");
          intendedIntensity <- getDouble(values, "intendedIntensity");
          classification <- getRequestedClassification(values, "classification")
        ) yield Some(SessionProperties(startDate, muscleGroupKeys, intendedIntensity, classification))).getOrElse(None)
      }

      (JsonParser.parse(request.entity.asString) match {
        case JObject(list) ⇒ getSessionProperties(list.toMap)
        case _ ⇒ None
      }).fold[Deserialized[SessionProperties]](Left(MalformedContent("Could not deserialize SessionProperties."))) { Right(_) }
    }
  }

  implicit object RequestedClassificationUnmarshaller extends FromStringOptionDeserializer[RequestedClassification] {
    def apply(value: Option[String]): Deserialized[RequestedClassification] = value match {
      case Some("RandomClassification") ⇒ Right(RandomClassification)
      case Some("ExplicitClassification") ⇒ Right(ExplicitClassification)
      case _ ⇒ Left(MalformedContent(s"Could not deserialize '$value' as RequestedClassification", None))
    }
  }

  implicit val RequestedClassificationMarshaller =
    Marshaller.of[RequestedClassification](ContentTypes.`application/json`) {
      (value, contentType, context) ⇒
        value match {
          case RandomClassification ⇒ context.marshalTo(HttpEntity(contentType, "RandomClassification"))
          case ExplicitClassification ⇒ context.marshalTo(HttpEntity(contentType, "ExplicitClassification"))
        }
    }

  implicit object MultiPacketFromRequestUnmarshaller extends FromRequestUnmarshaller[MultiPacket] {
    override def apply(request: HttpRequest): Deserialized[MultiPacket] = {
      MultiPacketDecoder.decode(request.entity.data.toByteString.asByteBuffer).fold(x ⇒ Left(MalformedContent(x)), Right.apply)
    }
  }

  implicit object SuggestionsFromRequestUnmarshaller extends FromRequestUnmarshaller[Suggestions] {
    private val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

    private def suggestion(value: JValue): Suggestion = value match {
      /*
         { "date":"2015-02-20",
           "source":"history",
           "musgleGroupKeys": ["arms"],
           "intensity": 1.0
         } ||
         { "date":"2015-02-21",
           "source":"programme"
         } ||
         { "date":"2015-02-22",
           "source": { "notes":"Chop, chop" },
           "muscleGroupKeys": ["legs"],
           "intensity":0.8
         }
       */
      case JObject(obj) ⇒
        val fields = obj.toMap
        val JString(ds) = fields("date")
        val date = dateFormat.parse(ds)
        val source = fields("source") match {
          case JString("history") ⇒ SuggestionSource.History
          case JString("programme") ⇒ SuggestionSource.Programme
          case JObject(trainer) ⇒
            val notes = trainer.toMap.get("notes").map { case JString(x) ⇒ x} .getOrElse("")
            SuggestionSource.Trainer(notes)
        }

        (fields.get("muscleGroupKeys"), fields.get("intensity")) match {
          case (Some(JArray(mgks)), Some(JDouble(intensity))) ⇒
            Suggestion.Session(date, source, mgks.map { case JString(x) ⇒ x}, intensity)
          case _ ⇒
            Suggestion.Rest(date, source)
        }
    }

    override def apply(request: HttpRequest): Deserialized[Suggestions] = {
      val json = JsonParser.parse(request.entity.asString, false)
      json match {
        case JArray(suggestions) ⇒ Right(Suggestions(suggestions.map(suggestion)))
        case _ ⇒ Left(MalformedContent(":("))
      }
    }
  }


  implicit object UnitToResponseMarshaller extends ToResponseMarshaller[Unit] {
    override def apply(value: Unit, ctx: ToResponseMarshallingContext): Unit = ctx.marshalTo(HttpResponse(entity = HttpEntity("{}")))
  }

  val SessionIdValue: PathMatcher1[SessionId] = JavaUUID.map(SessionId.apply)

}
