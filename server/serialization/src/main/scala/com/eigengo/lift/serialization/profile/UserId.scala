package com.eigengo.lift.serialization.profile

import java.util.UUID

case class UserId(id: UUID) extends AnyVal {
  override def toString: String = id.toString
}
object UserId {
  def randomId(): UserId = UserId(UUID.randomUUID())
  def apply(s: String): UserId = UserId(UUID.fromString(s))
}
