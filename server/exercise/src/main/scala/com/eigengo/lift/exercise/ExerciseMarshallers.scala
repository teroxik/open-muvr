package com.eigengo.lift.exercise

import com.eigengo.lift.common.{CommonMarshallers, CommonPathDirectives}
import spray.http.{HttpEntity, _}
import spray.httpx.marshalling._
import spray.httpx.unmarshalling._
import spray.routing.PathMatcher1
import spray.routing.directives.{MarshallingDirectives, PathDirectives}

import scala.language.implicitConversions

/**
 * Defines the marshallers for the Lift system
 */
trait ExerciseMarshallers
  extends MarshallingDirectives
  with PathDirectives
  with CommonPathDirectives
  with CommonMarshallers
  with SuggestionsMarshallers{

  implicit object MultiPacketFromRequestUnmarshaller extends FromRequestUnmarshaller[MultiPacket] {
    override def apply(request: HttpRequest): Deserialized[MultiPacket] = {
      MultiPacketDecoder.decode(request.entity.data.toByteString.asByteBuffer).fold(x ⇒ Left(MalformedContent(x)), Right.apply)
    }
  }

  implicit object UnitToResponseMarshaller extends ToResponseMarshaller[Unit] {
    override def apply(value: Unit, ctx: ToResponseMarshallingContext): Unit = ctx.marshalTo(HttpResponse(entity = HttpEntity("{}")))
  }

  val SessionIdValue: PathMatcher1[SessionId] = JavaUUID.map(SessionId.apply)
}
