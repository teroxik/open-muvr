package com.eigengo.lift.exercise

import java.text.SimpleDateFormat

import com.eigengo.lift.Exercise.Suggestion.Session
import com.eigengo.lift.Exercise.SuggestionSource.{History, Programme, Trainer}
import com.eigengo.lift.Exercise.{SuggestionSource, Suggestion, Suggestions}
import org.json4s.JsonAST.{JDouble, JArray, JString, JObject}
import org.json4s._
import org.json4s.native.JsonParser
import spray.http.ContentTypes._
import spray.http.{HttpEntity, HttpRequest}
import spray.httpx.marshalling.{MarshallingContext, Marshaller}
import spray.httpx.unmarshalling._

import scala.language.implicitConversions

trait SuggestionsMarshallers {
  implicit object SuggestionsFromRequestUnmarshaller extends FromRequestUnmarshaller[Suggestions] {
    private val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

    private def suggestion(value: JValue): Suggestion = (value: @unchecked) match {
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
        val source = (fields("source"): @unchecked) match {
          case JString("history") ⇒
            SuggestionSource.History
          case JString("programme") ⇒
            SuggestionSource.Programme
          case JObject(trainer) ⇒
            val notes = trainer.toMap.get("notes").flatMap {
              case JString(x) ⇒
                Some(x)
              case _ =>
                None
            }.getOrElse("")
            SuggestionSource.Trainer(notes)
        }

        (fields.get("muscleGroupKeys"), fields.get("intensity")) match {
          case (Some(JArray(mgks)), Some(JDouble(intensity))) ⇒
            Suggestion.Session(date, source, mgks.flatMap {
              case JString(x) ⇒
                Some(x)
              case _ =>
                None
            }, intensity)
          case _ ⇒
            Suggestion.Rest(date, source)
        }
    }

    override def apply(request: HttpRequest): Deserialized[Suggestions] = {
      val json = JsonParser.parse(request.entity.asString, false)
      json match {
        case JArray(suggestions) ⇒
          Right(Suggestions(suggestions.map(suggestion)))
        case _ ⇒
          Left(MalformedContent(":("))
      }
    }
  }

  implicit object SuggestionsToResponseMarshaller extends Marshaller[Suggestions] {

    import org.json4s.JsonDSL._
    import org.json4s.jackson.JsonMethods._

    override def apply(value: Suggestions, ctx: MarshallingContext): Unit = {

      val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

      implicit def suggestionSource(s: SuggestionSource): JValue = s match {
        case Trainer(n) => JObject(JField("notes", JString(n)))
        case Programme => JString(Programme.toString.toLowerCase)
        case History => JString(History.toString.toLowerCase)
      }

      val response: JValue = value.suggestions.map {
        case Session(d, s, m, i) =>
          ("date" -> dateFormat.format(d)) ~
            ("source" -> s) ~
            ("muscleGroupKeys" -> m) ~
            ("intensity" -> i)
        case Suggestion.Rest(d, s) =>
          ("date" -> dateFormat.format(d)) ~
            ("source" -> s)
      }

      ctx.marshalTo(HttpEntity(`application/json`, compact(render(response))))
    }
  }
}
