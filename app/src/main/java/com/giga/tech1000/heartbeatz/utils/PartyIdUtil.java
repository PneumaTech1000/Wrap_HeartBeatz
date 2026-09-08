package com.giga.tech1000.heartbeatz.utils;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Utility class for encoding and decoding party IDs securely.
 * This helps prevent exposing raw UUIDs and user IDs to potential attackers.
 */
public class PartyIdUtil {

    private static final String SEPARATOR = "::";
    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Encodes a party ID and user ID into a secure token.
     *
     * @param partyId The party UUID
     * @param userId The Firebase user ID
     * @return A base64-encoded secure token
     */
    public static String encodePartyId(String partyId, String userId) {
        if (partyId == null || userId == null) {
            throw new IllegalArgumentException("Party ID and User ID must not be null");
        }

        try {
            // Combine the IDs with a separator
            String combined = partyId + SEPARATOR + userId;

            // Create a hash of the combined string for verification
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));

            // Combine the original string with its hash
            String toEncode = combined + SEPARATOR + bytesToHex(hash);

            // Base64 encode for transmission/storage
            return Base64.encodeToString(
                    toEncode.getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP | Base64.URL_SAFE);
        } catch (NoSuchAlgorithmException e) {
            // This should never happen as SHA-256 is always available
            throw new RuntimeException("Failed to hash party ID", e);
        }
    }

    /**
     * Decodes a secure token back to its original party ID and user ID.
     *
     * @param token The base64-encoded secure token
     * @return An array where [0] is partyId and [1] is userId, or null if invalid
     */
    public static String[] decodePartyId(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        try {
            // Decode from base64
            byte[] decodedBytes = Base64.decode(token, Base64.NO_WRAP | Base64.URL_SAFE);
            String decoded = new String(decodedBytes, StandardCharsets.UTF_8);

            // Split into parts: partyId::userId::hash
            String[] parts = decoded.split(java.util.regex.Pattern.quote(SEPARATOR), 3);
            if (parts.length != 3) {
                return null; // Invalid format
            }

            String partyId = parts[0];
            String userId = parts[1];
            String providedHash = parts[2];

            // Verify the hash
            String combined = partyId + SEPARATOR + userId;
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] computedHash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            String computedHashHex = bytesToHex(computedHash);

            if (!MessageDigest.isEqual(
                    Utils.hexStringToByteArray(providedHash),
                    computedHash)) {
                return null; // Hash mismatch - token may be tampered with
            }

            return new String[]{partyId, userId};
        } catch (Exception e) {
            // Any exception means the token is invalid
            return null;
        }
    }

    /**
     * Converts a byte array to a hexadecimal string.
     *
     * @param bytes The byte array to convert
     * @return Hexadecimal string representation
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Utility class for hex string conversions.
     */
    private static class Utils {
        private Utils() { }

        public static byte[] hexStringToByteArray(String s) {
            int len = s.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                     + Character.digit(s.charAt(i+1), 16));
            }
            return data;
        }
    }
}