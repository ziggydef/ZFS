package com.becamce.app.core.data

/**
 * Public Supabase project configuration only.
 * Never place the service-role key in the Android client.
 */
data class BackendConfig(
    val projectUrl: String,
    val publishableKey: String
) {
    val isConfigured: Boolean
        get() = projectUrl.startsWith("https://") &&
            projectUrl.contains(".supabase.co") &&
            publishableKey.isNotBlank() &&
            !publishableKey.contains("YOUR_", ignoreCase = true)
}
