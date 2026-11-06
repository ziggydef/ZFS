package com.becamce.app.core.domain

import com.becamce.app.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

data class AuthSession(
    val userId: String,
    val accessTokenExpiresAtEpochSeconds: Long
)

interface AuthRepository {
    val session: Flow<AuthSession?>
    suspend fun signUp(email: String, password: String, displayName: String): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<AuthSession>
    suspend fun requestPhoneCode(phone: String, displayName: String): Result<Unit>
    suspend fun verifyPhoneCode(phone: String, code: String): Result<AuthSession>
    suspend fun refreshSessionIfNeeded(): Result<AuthSession?>
    suspend fun signOut(): Result<Unit>
    suspend fun requestPasswordReset(email: String): Result<Unit>
}

interface ProfileRepository {
    suspend fun getMyProfile(): Result<UserProfile>
    suspend fun updateMyProfile(displayName: String, username: String?, bio: String): Result<UserProfile>
    suspend fun isUsernameAvailable(username: String): Result<Boolean>
}
