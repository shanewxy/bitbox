package unimelb.bitbox.util;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Base64.Encoder;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author: Xueying Wang
 */
public class SecurityUtil {
    public static String encrypt(String json, SecretKey secretKey) {
        Document doc = new Document();
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            Encoder encoder = Base64.getEncoder();
            byte[] encrypted = cipher.doFinal(json.getBytes("UTF-8"));
            doc.append("payload", encoder.encodeToString(encrypted));
        } catch (InvalidKeyException e) {

            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {

            e.printStackTrace();
        } catch (NoSuchPaddingException e) {

            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {

            e.printStackTrace();
        } catch (BadPaddingException e) {

            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {

            e.printStackTrace();
        }
        return doc.toJson();
    }

    public static String decrypt(String json, SecretKey secretKey) {
        String payload = Document.parse(json).getString("payload");
        String decrpted = "";
        byte[] decoded = Base64.getDecoder().decode(payload);
        try {
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            decrpted = new String(cipher.doFinal(decoded));
        } catch (InvalidKeyException e) {

            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {

            e.printStackTrace();
        } catch (NoSuchPaddingException e) {

            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {

            e.printStackTrace();
        } catch (BadPaddingException e) {

            e.printStackTrace();
        }
        return decrpted;
    }
}
