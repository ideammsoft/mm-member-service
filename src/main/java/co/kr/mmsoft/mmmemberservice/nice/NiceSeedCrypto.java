package co.kr.mmsoft.mmmemberservice.nice;

import org.bouncycastle.crypto.engines.SEEDEngine;
import org.bouncycastle.crypto.modes.CBCBlockCipher;
import org.bouncycastle.crypto.paddings.PKCS7Padding;
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * NICE CheckPlus 구형 API (v1) SEED-128 암호화/복호화 유틸리티
 *
 * 구형 API 키 생성 규칙:
 *   - KEY: sSitePassword 를 UTF-8 바이트로 변환, 16바이트로 0-패딩 or 절삭
 *   - IV : sSiteCode    를 UTF-8 바이트로 변환, 16바이트로 0-패딩 or 절삭
 *   - 알고리즘: SEED-128 CBC + PKCS7 패딩
 *   - 인코딩: Base64 (URL-safe 아닌 표준 Base64)
 *   - 문자셋: EUC-KR (NICE 서버와 동일하게 맞춰야 함)
 */
public class NiceSeedCrypto {

    private static final Charset EUC_KR = Charset.forName("EUC-KR");

    // 16바이트 키/IV 생성 (부족하면 0 패딩, 초과하면 절삭)
    private static byte[] toKey16(String s) {
        byte[] src = s.getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[16];
        System.arraycopy(src, 0, key, 0, Math.min(src.length, 16));
        return key;
    }

    /**
     * 평문 → 암호문 (Base64)
     * @param plainText 암호화할 평문 (EUC-KR)
     * @param siteCode  NICE 사이트 코드 (IV 용도)
     * @param sitePass  NICE 사이트 패스워드 (KEY 용도)
     */
    public static String encrypt(String plainText, String siteCode, String sitePass) {
        try {
            byte[] key    = toKey16(sitePass);
            byte[] iv     = toKey16(siteCode);
            byte[] plain  = plainText.getBytes(EUC_KR);

            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                    new CBCBlockCipher(new SEEDEngine()), new PKCS7Padding());
            cipher.init(true, new ParametersWithIV(new KeyParameter(key), iv));

            byte[] out  = new byte[cipher.getOutputSize(plain.length)];
            int    len  = cipher.processBytes(plain, 0, plain.length, out, 0);
            len += cipher.doFinal(out, len);

            return Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(out, len));
        } catch (Exception e) {
            throw new RuntimeException("NICE SEED 암호화 실패", e);
        }
    }

    /**
     * 암호문 (Base64) → 평문
     * @param encData  NICE로부터 수신한 Base64 인코딩 암호문
     * @param siteCode NICE 사이트 코드 (IV 용도)
     * @param sitePass NICE 사이트 패스워드 (KEY 용도)
     * @return 복호화된 평문 (EUC-KR 디코딩)
     */
    public static String decrypt(String encData, String siteCode, String sitePass) {
        try {
            byte[] key      = toKey16(sitePass);
            byte[] iv       = toKey16(siteCode);
            byte[] cipherBytes = Base64.getDecoder().decode(encData.trim());

            PaddedBufferedBlockCipher cipher = new PaddedBufferedBlockCipher(
                    new CBCBlockCipher(new SEEDEngine()), new PKCS7Padding());
            cipher.init(false, new ParametersWithIV(new KeyParameter(key), iv));

            byte[] out = new byte[cipher.getOutputSize(cipherBytes.length)];
            int    len = cipher.processBytes(cipherBytes, 0, cipherBytes.length, out, 0);
            len += cipher.doFinal(out, len);

            return new String(java.util.Arrays.copyOf(out, len), EUC_KR);
        } catch (Exception e) {
            throw new RuntimeException("NICE SEED 복호화 실패", e);
        }
    }
}
