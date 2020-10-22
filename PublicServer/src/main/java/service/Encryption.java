package main.java.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;

public class Encryption {

    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 160;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA1";
    private static final SecureRandom RAND = new SecureRandom();

    public Encryption() {
    }

    public static Optional<String> generateSalt(final int length) {

        if (length < 1) {
            System.out.println("error in generateSalt: length must be > 0");
            return Optional.empty();
        }

        byte[] salt = new byte[length];
        RAND.nextBytes(salt);

        return Optional.of(Base64.getEncoder().encodeToString(salt));
    }

    public static Optional<String> encrypt(String value, String salt) throws Exception {

        char[] chars = value.toCharArray();
        byte[] bytes = salt.getBytes();

        PBEKeySpec spec = new PBEKeySpec(chars, bytes, ITERATIONS, KEY_LENGTH);

        Arrays.fill(chars, Character.MIN_VALUE);

        try {
            SecretKeyFactory fac = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] securePassword = fac.generateSecret(spec).getEncoded();
            // return the encrypted value as a string here
            return Optional.of(Base64.getEncoder().encodeToString(securePassword));

        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new Exception("Exception in the encrypt value ");

        } finally {
            spec.clearPassword();
        }
    }

    //Testing the encrypted value
    public static boolean verifyValue(String value, String key, String salt) throws Exception {

        Optional<String> optEncrypted = encrypt(value, salt);
        if (!optEncrypted.isPresent()) {
            return false;
        }else
        return optEncrypted.get().equals(key);
    }
}
