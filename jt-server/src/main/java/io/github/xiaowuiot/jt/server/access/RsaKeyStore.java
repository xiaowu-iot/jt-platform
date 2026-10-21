package io.github.xiaowuiot.jt.server.access;

import io.github.xiaowuiot.protocol.jt808.codec.Jt808Rsa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * 808 平台 RSA 密钥对的保存：第一次启动生成，之后一直用这一对。
 * 终端手里存的是平台公钥，密钥对变了终端发来的密文就解不开了，所以不能每次启动都重新生成。
 * <p>
 * 文件两行：公钥（X.509）、私钥（PKCS#8），都是 Base64。私钥文件要和数据库一样备份、限制访问权限。
 */
final class RsaKeyStore {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyStore.class);

    private RsaKeyStore() {
    }

    static KeyPair load(Path file) {
        try {
            if (Files.exists(file)) {
                List<String> lines = Files.readAllLines(file, StandardCharsets.US_ASCII);
                KeyFactory f = KeyFactory.getInstance("RSA");
                Base64.Decoder d = Base64.getDecoder();
                return new KeyPair(f.generatePublic(new X509EncodedKeySpec(d.decode(lines.get(0).trim()))),
                        f.generatePrivate(new PKCS8EncodedKeySpec(d.decode(lines.get(1).trim()))));
            }
            KeyPair kp = Jt808Rsa.newKeyPair();
            Files.createDirectories(file.toAbsolutePath().getParent());
            Base64.Encoder e = Base64.getEncoder();
            Files.write(file, (e.encodeToString(kp.getPublic().getEncoded()) + "\n"
                    + e.encodeToString(kp.getPrivate().getEncoded()) + "\n").getBytes(StandardCharsets.US_ASCII));
            log.info("已生成 808 平台 RSA 密钥对：{}（请和数据库一起备份）", file.toAbsolutePath());
            return kp;
        } catch (IOException | GeneralSecurityException | RuntimeException ex) {
            throw new IllegalStateException("读取 808 平台 RSA 密钥失败：" + file.toAbsolutePath() + "，" + ex.getMessage(), ex);
        }
    }
}
