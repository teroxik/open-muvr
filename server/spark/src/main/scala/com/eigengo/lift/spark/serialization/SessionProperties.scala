package com.eigengo.lift.exercise

import java.util.{Date, UUID}

/**
 * The session identity
 * @param id the id
 */
@SerialVersionUID(1010l) case class SessionId(id: UUID) extends AnyVal {
  override def toString = id.toString
}

@SerialVersionUID(1011l) object SessionId {
  def apply(s: String): SessionId = SessionId(UUID.fromString(s))
  def randomId(): SessionId = SessionId(UUID.randomUUID())
}