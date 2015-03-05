package com.eigengo.lift.profile

import com.eigengo.lift.common.UserId
import com.eigengo.lift.profile.UserProfileProtocol.Account

object UserProfile {
  /**
   * Registers a user
   * @param userId the user to be added
   * @param account the user account
   */
  @SerialVersionUID(1014l) case class UserRegistered(userId: UserId, account: Account)
}
