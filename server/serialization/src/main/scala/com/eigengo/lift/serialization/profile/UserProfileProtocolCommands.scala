package com.eigengo.lift.serialization.profile

import com.eigengo.lift.serialization.profile.UserId

object UserProfileProtocolCommands {
  /**
   * The user account details
   * @param email the user's email
   * @param password the hashed password
   * @param salt the salt used in hashing
   */
  case class Account(email: String, password: Array[Byte], salt: String)
}
