package com.eigengo.lift.profile

object UserProfileProtocol {

  /**
   * The user account details
   * @param email the user's email
   * @param password the hashed password
   * @param salt the salt used in hashing
   */
  @SerialVersionUID(1017l) case class Account(email: String, password: Array[Byte], salt: String)
}