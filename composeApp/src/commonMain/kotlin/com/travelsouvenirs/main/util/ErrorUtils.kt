package com.travelsouvenirs.main.util

/**
 * Utility functions for formatting exception messages into user-friendly strings,
 * especially handling native iOS NSError outputs wrapped by Kotlin Multiplatform libraries.
 */
object ErrorUtils {
    
    fun getFriendlyErrorMessage(throwable: Throwable): String {
        val message = throwable.message ?: return "An unknown error occurred."
        
        // 1. Explicit Firebase iOS Error (NSError) mapping to clean, localized messages
        if (message.contains("FIRAuthErrorDomain")) {
            return when {
                message.contains("ERROR_WRONG_PASSWORD") || message.contains("17009") -> 
                    "Incorrect password. Please try again."
                message.contains("ERROR_USER_NOT_FOUND") || message.contains("17011") -> 
                    "No account found with this email address."
                message.contains("ERROR_INVALID_EMAIL") || message.contains("17008") -> 
                    "Invalid email address format."
                message.contains("ERROR_EMAIL_ALREADY_IN_USE") || message.contains("17007") -> 
                    "This email address is already in use by another account."
                message.contains("ERROR_WEAK_PASSWORD") || message.contains("17026") -> 
                    "The password is too weak. It must be at least 6 characters."
                message.contains("ERROR_USER_DISABLED") || message.contains("17005") -> 
                    "This user account has been disabled."
                message.contains("ERROR_TOO_MANY_REQUESTS") || message.contains("17010") -> 
                    "Too many unsuccessful login attempts. Please try again later."
                message.contains("ERROR_NETWORK_REQUEST_FAILED") || message.contains("17020") -> 
                    "Network error. Please check your internet connection."
                else -> parseLocalizedDescription(message)
            }
        }
        
        // 2. Try parsing generic NSLocalizedDescription if present
        if (message.contains("NSLocalizedDescription=")) {
            return parseLocalizedDescription(message)
        }
        
        // 3. Fallback to clean standard Firebase Android error messages or original message
        return when {
            message.contains("password is invalid") || message.contains("wrong password") -> 
                "Incorrect password. Please try again."
            message.contains("no user record") || message.contains("user not found") -> 
                "No account found with this email address."
            message.contains("email address is badly formatted") -> 
                "Invalid email address format."
            message.contains("email address is already in use") -> 
                "This email address is already in use by another account."
            else -> message
        }
    }
    
    private fun parseLocalizedDescription(message: String): String {
        val key = "NSLocalizedDescription="
        val startIdx = message.indexOf(key) + key.length
        val remaining = message.substring(startIdx)
        
        // Check for typical delimiters separating userInfo dictionary entries on iOS
        val nextKeys = listOf(", error_name=", ", NS", ", ", "}", "]")
        var endIdx = -1
        for (nextKey in nextKeys) {
            val idx = remaining.indexOf(nextKey)
            if (idx != -1) {
                endIdx = idx
                break
            }
        }
        
        if (endIdx != -1) {
            val extracted = remaining.substring(0, endIdx).trim()
            if (extracted.isNotEmpty()) {
                return extracted
            }
        }
        
        return message
    }
}
