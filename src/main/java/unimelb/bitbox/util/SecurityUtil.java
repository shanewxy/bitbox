package unimelb.bitbox.util;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.logging.Logger;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import unimelb.bitbox.MessageHandler;

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
            while (json.length() % 16 != 0) {
                Random rdm = new Random();
                json += rdm.nextInt();
            }
            byte[] encrypted = cipher.doFinal(json.getBytes("UTF-8"));
            Encoder encoder = Base64.getEncoder();
            doc.append("payload", encoder.encodeToString(encrypted));
        } catch (InvalidKeyException e) {

            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {

            e.printStackTrace();
        } catch (NoSuchPaddingException e) {

            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            log.warning(e.getMessage());
            System.exit(1);
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
        if (payload != null) {

            byte[] decoded = Base64.getDecoder().decode(payload);
            try {
                Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, secretKey);
                System.out.println(new String(cipher.doFinal(decoded)));
                decrpted = new String(cipher.doFinal(decoded)).split(System.lineSeparator())[0];
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
        }
        return decrpted;
    }
}
