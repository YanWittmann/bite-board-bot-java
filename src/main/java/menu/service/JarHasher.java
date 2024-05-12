package menu.service;

import java.io.*;
import java.security.*;

public class JarHasher {
    public static String getJarHash() throws Exception {
        String jarFilePath = JarHasher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
        return calculateHash(jarFilePath);
    }

    private static String calculateHash(String filePath) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(readFileBytes(filePath));
        return bytesToHex(hashBytes);
    }

    private static byte[] readFileBytes(String filePath) throws IOException {
        File file = new File(filePath);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
