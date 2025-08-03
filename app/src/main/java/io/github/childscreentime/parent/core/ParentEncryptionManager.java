package io.github.childscreentime.parent.core;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;

/**
 * Manages encryption for parent-child communication using child device ID
 */
public class ParentEncryptionManager {
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    
    private SecretKey encryptionKey;
    
    public ParentEncryptionManager(String childDeviceId) {
        this.encryptionKey = createKeyFromDeviceId(childDeviceId);
    }
    
    /**
     * Generate encryption key from child device ID using SHA-256 for consistency
     */
    private SecretKey createKeyFromDeviceId(String deviceId) {
        try {
            // Use SHA-256 to derive a consistent 256-bit key from device ID
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(deviceId.getBytes(StandardCharsets.UTF_8));
            
            // Use first 16 bytes for AES-128
            byte[] aesKeyBytes = new byte[16];
            System.arraycopy(keyBytes, 0, aesKeyBytes, 0, 16);
            
            return new SecretKeySpec(aesKeyBytes, ALGORITHM);
        } catch (Exception e) {
            android.util.Log.e("ParentEncryptionManager", "Failed to create encryption key - parent discovery disabled", e);
            throw new RuntimeException("Key creation failed", e);
        }
    }
    
    /**
     * Encrypt a message using the child device's encryption key
     */
    public String encryptMessage(String message) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            
            // Generate IV for CBC mode
            byte[] iv = new byte[16]; // AES block size
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, ivSpec);
            
            byte[] encryptedBytes = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
            
            // Prepend IV to encrypted data
            byte[] encryptedWithIv = new byte[iv.length + encryptedBytes.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, iv.length);
            System.arraycopy(encryptedBytes, 0, encryptedWithIv, iv.length, encryptedBytes.length);
            
            return Base64.encodeToString(encryptedWithIv, Base64.DEFAULT);
        } catch (Exception e) {
            android.util.Log.e("ParentEncryptionManager", "Failed to encrypt message - parent discovery disabled", e);
            throw new RuntimeException("Message encryption failed", e);
        }
    }
    
    /**
     * Decrypt a message using the child device's encryption key
     */
    public String decryptMessage(String encryptedMessage) {
        try {
            byte[] encryptedWithIv = Base64.decode(encryptedMessage, Base64.DEFAULT);
            
            // Extract IV from the beginning
            byte[] iv = new byte[16]; // AES block size
            byte[] encryptedBytes = new byte[encryptedWithIv.length - 16];
            
            System.arraycopy(encryptedWithIv, 0, iv, 0, 16);
            System.arraycopy(encryptedWithIv, 16, encryptedBytes, 0, encryptedBytes.length);
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, ivSpec);
            
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            android.util.Log.e("ParentEncryptionManager", "Failed to decrypt message - parent discovery disabled", e);
            throw new RuntimeException("Message decryption failed", e);
        }
    }
}
