package com.eigengo.lift.spark.api

import com.eigengo.lift.Suggestions
import spray.http.HttpResponse
import spray.httpx.marshalling.{MarshallingContext, Marshaller, ToResponseMarshallingContext, ToResponseMarshaller}

trait ExerciseMarshallers {

  implicit object SuggestionsToResponseMarshaller extends Marshaller[Suggestions] {

  /*  override def apply(value: Suggestions, ctx: ToResponseMarshallingContext): Unit = {
      ctx.marshalTo(HttpResponse(entity = value.toString))
    }*/
    override def apply(value: Suggestions, ctx: MarshallingContext): Unit = ???
  }
}
