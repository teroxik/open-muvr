package com.eigengo.lift.common

import java.util.UUID

@SerialVersionUID(1015l) case class UserId(id: UUID) extends AnyVal {
  override def toString: String = id.toString
}
@SerialVersionUID(1016l) object UserId {
  def randomId(): UserId = UserId(UUID.randomUUID())
  def apply(s: String): UserId = UserId(UUID.fromString(s))
}