package com.eigengo.lift.spark.api

import java.text.SimpleDateFormat

import com.eigengo.lift.Suggestion.{Rest, Session}
import com.eigengo.lift.SuggestionSource.{History, Programme, Trainer}
import com.eigengo.lift.{SuggestionSource, Suggestions}
import spray.http.ContentTypes._
import spray.http.HttpEntity
import spray.httpx.marshalling.{MarshallingContext, Marshaller}

/**
 * Spark HTTP requests marshalling
 */
trait ExerciseMarshallers {
  import org.json4s._
  import org.json4s.JsonDSL._
  import org.json4s.jackson.JsonMethods._

  /**
   * Marshalling of Spark suggestions
   */
  implicit object SuggestionsToResponseMarshaller extends Marshaller[Suggestions] {

    override def apply(value: Suggestions, ctx: MarshallingContext): Unit = {

      val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

      implicit def suggestionSource: SuggestionSource => JValue = s => s match {
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
        case Rest(d, s) =>
          ("date" -> dateFormat.format(d)) ~
          ("source" -> s)
      }

      ctx.marshalTo(HttpEntity(`application/json`, compact(render(response))))
    }
  }
}
