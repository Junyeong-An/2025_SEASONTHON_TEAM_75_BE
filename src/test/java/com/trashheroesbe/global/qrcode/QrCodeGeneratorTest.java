package com.trashheroesbe.global.qrcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.trashheroesbe.global.exception.BusinessException;
import com.trashheroesbe.global.response.type.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QrCodeGeneratorTest {

    private final QrCodeGenerator qrCodeGenerator = new QrCodeGenerator();

    @Test
    @DisplayName("요청 크기의 PNG QR 이미지를 생성하고 내용을 다시 읽을 수 있다")
    void 생성_라운드트립() throws Exception {
        // given
        String content = "https://trash-heroes.store/qr?userCouponId=100&qrToken=token-123";

        // when
        byte[] png = qrCodeGenerator.generatePngBytes(content, 300);

        // then
        assertThat(png).isNotEmpty();
        assertThat(png[0]).isEqualTo((byte) 0x89);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(300);
        assertThat(image.getHeight()).isEqualTo(300);

        Result decoded = new MultiFormatReader().decode(new BinaryBitmap(
            new HybridBinarizer(new BufferedImageLuminanceSource(image))));
        assertThat(decoded.getText()).isEqualTo(content);
    }

    @Test
    @DisplayName("내용이 비어 있으면 QR_GENERATION_FAIL 예외가 발생한다")
    void 빈_내용_예외() {
        // when & then
        assertThatThrownBy(() -> qrCodeGenerator.generatePngBytes("", 300))
            .isInstanceOfSatisfying(BusinessException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QR_GENERATION_FAIL));
    }
}
