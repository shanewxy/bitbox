package unimelb.bitbox.util;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Random;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.bouncycastle.util.Arrays;

/**
 * @author: Xueying Wang
 */
public class SecurityUtil {
    private static Logger log = Logger.getLogger(SecurityUtil.class.getName());

    public static String encrypt(String json, SecretKey secretKey) {
        json += System.lineSeparator();
        Document doc = new Document();
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] jsonbytes = json.getBytes("UTF-8");
            int bytesToPad = 16 - jsonbytes.length % 16;
            if (bytesToPad != 16) {
                Random rdm = new Random();
                byte[] bytes = new byte[bytesToPad];
                rdm.nextBytes(bytes);
                jsonbytes = Arrays.concatenate(jsonbytes, bytes);
            }
            byte[] encrypted = cipher.doFinal(jsonbytes);
            Encoder encoder = Base64.getEncoder();
            doc.append("payload", encoder.encodeToString(encrypted));
        } catch (InvalidKeyException e) {
            log.severe(e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            log.severe(e.getMessage());
        } catch (NoSuchPaddingException e) {
            log.severe(e.getMessage());
        } catch (IllegalBlockSizeException e) {
            log.warning(e.getMessage());
            System.exit(1);
        } catch (BadPaddingException e) {
            log.severe(e.getMessage());
        } catch (UnsupportedEncodingException e) {
            log.severe(e.getMessage());
        }
        return doc.toJson();
    }

    public static String decrypt(String json, SecretKey secretKey) {
        String payload = Document.parse(json).getString("payload");
        String decrpted = "";
        if (payload != null) {
            byte[] decoded = Base64.getDecoder().decode(payload);
            try {
                Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, secretKey);
                log.info(new String(cipher.doFinal(decoded)));
                decrpted = new String(cipher.doFinal(decoded)).split(System.lineSeparator())[0];
            } catch (InvalidKeyException e) {
                log.severe(e.getMessage());
            } catch (NoSuchAlgorithmException e) {
                log.severe(e.getMessage());
            } catch (NoSuchPaddingException e) {
                log.severe(e.getMessage());
            } catch (IllegalBlockSizeException e) {
                log.severe(e.getMessage());
            } catch (BadPaddingException e) {
                log.severe(e.getMessage());
            }
        }
        return decrpted;
    }

    public static byte[] padAESKey(byte[] keyBytes) {
        Random rdm = new Random();
        byte[] input = new byte[239];
        rdm.nextBytes(input);
        byte[] padded = Arrays.concatenate(keyBytes, input);
        return padded;
    }
}
