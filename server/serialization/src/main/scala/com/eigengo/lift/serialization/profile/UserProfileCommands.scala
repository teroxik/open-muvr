package com.eigengo.lift.serialization.profile

import com.eigengo.lift.serialization.profile.UserProfileProtocolCommands.Account

object UserProfileCommands {
  /**
   * Registers a user
   * @param userId the user to be added
   * @param account the user account
   */
  case class UserRegistered(userId: UserId, account: Account)
}
