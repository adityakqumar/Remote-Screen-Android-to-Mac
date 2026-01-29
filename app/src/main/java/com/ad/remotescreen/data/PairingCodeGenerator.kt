package com.ad.remotescreen.data

import java.security.SecureRandom

/**
 * Generates secure one-time pairing codes for device pairing.
 * Uses SecureRandom for cryptographically secure random number generation.
 */
object PairingCodeGenerator {
    
    private const val CODE_LENGTH = 6
    private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // No O, 0, 1, I for clarity
    
    private val secureRandom = SecureRandom()
    
    /**
     * Generates a new secure pairing code.
     * The code is 6 characters long and uses only unambiguous characters.
     * 
     * @return A new pairing code (e.g., "A7K3M9")
     */
    fun generate(): String {
        return buildString {
            repeat(CODE_LENGTH) {
                append(CODE_CHARS[secureRandom.nextInt(CODE_CHARS.length)])
            }
        }
    }
    
    /**
     * Validates the format of a pairing code.
     * 
     * @param code The code to validate
     * @return true if the code format is valid
     */
    fun isValidFormat(code: String): Boolean {
        if (code.length != CODE_LENGTH) return false
        return code.all { it in CODE_CHARS }
    }
    
    /**
     * Normalizes a pairing code (uppercase, trim whitespace).
     * 
     * @param code The code to normalize
     * @return The normalized code
     */
    fun normalize(code: String): String {
        return code.trim().uppercase().replace("O", "0").replace("I", "1")
    }
}
