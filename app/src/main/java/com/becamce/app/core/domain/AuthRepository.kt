package com.becamce.app.core.domain

import kotlinx.coroutines.flow.Flow

data class AuthSession(
    val userId: String,
    val accessTokenExpiresAtEpochSeconds: Long
)

interface AuthRepository {
    val session: Flow<AuthSession?>
    suspend fun signUp(username: String, password: String, displayName: String): Result<Unit>
    suspend fun signIn(username: String, password: String): Result<AuthSession>
    suspend fun refreshSessionIfNeeded(): Result<AuthSession?>
    suspend fun signOut(): Result<Unit>
}
