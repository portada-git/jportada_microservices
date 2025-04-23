/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.elsquatrecaps.portada.jportadamicroservice.cypher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author josep
 */
public class EncryptDecryptAes {
    private int iterations=100000;
    private SecureRandom secureRandom = new SecureRandom();
    
    public void init(int iterations){
        this.iterations = iterations;
    }
    
    public void encrypt(String string, String encryptedFilePath, String secret) throws NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException, IOException{
        byte[] toEncrypt = string.getBytes();
        
        // Extract salt and encrypted data
        byte[] salt = new byte[8];
        secureRandom.nextBytes(salt);

        // Derive the key and IV
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, 256 + 128);
        SecretKey tmp = factory.generateSecret(spec);
        byte[] keyAndIV = tmp.getEncoded();
        byte[] key = Arrays.copyOfRange(keyAndIV, 0, 32);
        byte[] iv = Arrays.copyOfRange(keyAndIV, 32, 48);
        
                // Initialize the cipher
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] cipherText = cipher.doFinal(toEncrypt);
        
        Files.write(Paths.get(encryptedFilePath), cipherText, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
        
    }
    
    
    public String decrypt(String encryptedFilePath, String secret) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException{
        byte[] fileContent = Files.readAllBytes(Paths.get(encryptedFilePath));

        System.out.println("s:" + secret);
        
        // Check for OpenSSL header "Salted__"
        byte[] saltHeader = Arrays.copyOfRange(fileContent, 0, 8);
        if (!Arrays.equals(saltHeader, "Salted__".getBytes("UTF-8"))) {
            throw new IllegalArgumentException("Invalid OpenSSL salt header");
        }
        
        // Extract salt and encrypted data
        byte[] salt = Arrays.copyOfRange(fileContent, 8, 16);
        byte[] encryptedData = Arrays.copyOfRange(fileContent, 16, fileContent.length);

        // Derive the key and IV
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, 256 + 128);
        SecretKey tmp = factory.generateSecret(spec);
        byte[] keyAndIV = tmp.getEncoded();
        byte[] key = Arrays.copyOfRange(keyAndIV, 0, 32);
        byte[] iv = Arrays.copyOfRange(keyAndIV, 32, 48);
        
        // Initialize the cipher
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

        // Decrypt the data
        byte[] decryptedData = cipher.doFinal(encryptedData);

        String ret =  new String(decryptedData, StandardCharsets.UTF_8);
        return ret.replace("\n", "").replace("\r", secret);        
    }
}
