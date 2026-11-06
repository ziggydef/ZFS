package com.becamce.app.core.domain

import com.becamce.app.core.model.UserProfile

interface ProfileRepository {
    suspend fun getMyProfile(): Result<UserProfile>
    suspend fun updateMyProfile(displayName: String, username: String?, bio: String): Result<UserProfile>
    suspend fun isUsernameAvailable(username: String): Result<Boolean>
}
