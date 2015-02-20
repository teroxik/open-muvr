package com.eigengo.lift.exercise

import java.text.SimpleDateFormat

import com.eigengo.lift.common.{CommonMarshallers, CommonPathDirectives}
import org.json4s.JsonAST._
import org.json4s.native.JsonParser
import spray.http.{HttpEntity, HttpRequest, HttpResponse}
import spray.httpx.marshalling.{ToResponseMarshaller, ToResponseMarshallingContext}
import spray.httpx.unmarshalling.{Deserialized, FromRequestUnmarshaller, MalformedContent}
import spray.routing._
import spray.routing.directives.{MarshallingDirectives, PathDirectives}

/**
 * Defines the marshallers for the Lift system
 */
trait ExerciseMarshallers extends MarshallingDirectives with PathDirectives with CommonPathDirectives with CommonMarshallers {

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
