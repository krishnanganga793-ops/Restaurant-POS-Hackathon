package com.restaurantpos.service;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Creates and verifies salted password hashes using Java's built-in cryptography. */
final class PasswordHasher {
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BITS = 256;
    private static final int ITERATIONS = 600_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    static PasswordHash create(String password) throws GeneralSecurityException {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password.toCharArray(), salt);
        return new PasswordHash(
            Base64.getEncoder().encodeToString(hash),
            Base64.getEncoder().encodeToString(salt)
        );
    }

    static boolean verify(String password, String encodedSalt, String encodedHash)
        throws GeneralSecurityException {
        byte[] salt;
        byte[] expectedHash;
        try {
            salt = Base64.getDecoder().decode(encodedSalt);
            expectedHash = Base64.getDecoder().decode(encodedHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        byte[] actualHash = derive(password.toCharArray(), salt);
        return MessageDigest.isEqual(expectedHash, actualHash);
    }

    private static byte[] derive(char[] password, byte[] salt) throws GeneralSecurityException {
        PBEKeySpec keySpec = new PBEKeySpec(password, salt, ITERATIONS, HASH_LENGTH_BITS);
        try {
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return keyFactory.generateSecret(keySpec).getEncoded();
        } finally {
            keySpec.clearPassword();
            Arrays.fill(password, '\0');
        }
    }

    record PasswordHash(String hash, String salt) {
    }
}
