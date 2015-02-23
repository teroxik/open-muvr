package com.eigengo.lift.spark.api

import com.eigengo.lift.Suggestion.Session
import com.eigengo.lift.Suggestions
import spray.http.{HttpEntity}
import spray.httpx.marshalling.{MarshallingContext, Marshaller}


trait ExerciseMarshallers {
  import org.json4s._
  import org.json4s.JsonDSL._
  import org.json4s.jackson.JsonMethods._

  implicit object SuggestionsToResponseMarshaller extends Marshaller[Suggestions] {

  /*  override def apply(value: Suggestions, ctx: ToResponseMarshallingContext): Unit = {
      ctx.marshalTo(HttpResponse(entity = value.toString))
    }*/
    override def apply(value: Suggestions, ctx: MarshallingContext): Unit = {
      val response: JValue = ("suggestions" -> value.suggestions.map { s => s match {
        case Session(d, s, m, i) =>
          ("date" -> d.toString) ~ ("source" -> s.toString) ~ ("muscleGroupKeys" -> m.toString) ~ ("intensity" -> i)
        case _ =>
          ("date" -> "") ~ ("source" -> "") ~ ("muscleGroupKeys" -> "") ~ ("intensity" -> "")
      }})

      ctx.marshalTo(HttpEntity(compact(render(response))))
    }
  }
}
