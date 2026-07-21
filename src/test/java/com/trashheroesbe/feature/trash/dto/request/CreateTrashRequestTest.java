package com.trashheroesbe.feature.trash.dto.request;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CreateTrashRequestTest {

    @Test
    @DisplayName("정상 이미지 파일은 검증을 통과한다")
    void 정상_이미지() {
        // given
        MockMultipartFile file = new MockMultipartFile(
            "imageFile", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

        // when & then
        assertThatCode(() -> new CreateTrashRequest(file).validate())
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("파일이 null이면 예외가 발생한다")
    void 파일_없음() {
        // when & then
        assertThatThrownBy(() -> new CreateTrashRequest(null).validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이미지 파일은 필수");
    }

    @Test
    @DisplayName("빈 파일이면 예외가 발생한다")
    void 빈_파일() {
        // given
        MockMultipartFile file = new MockMultipartFile(
            "imageFile", "photo.jpg", "image/jpeg", new byte[]{});

        // when & then
        assertThatThrownBy(() -> new CreateTrashRequest(file).validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이미지 파일은 필수");
    }

    @Test
    @DisplayName("이미지가 아닌 컨텐츠 타입이면 예외가 발생한다")
    void 이미지_아님() {
        // given
        MockMultipartFile file = new MockMultipartFile(
            "imageFile", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        // when & then
        assertThatThrownBy(() -> new CreateTrashRequest(file).validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("이미지 파일만");
    }

    @Test
    @DisplayName("10MB를 초과하는 파일이면 예외가 발생한다")
    void 크기_초과() {
        // given
        MockMultipartFile file = new MockMultipartFile(
            "imageFile", "big.jpg", "image/jpeg", new byte[10 * 1024 * 1024 + 1]);

        // when & then
        assertThatThrownBy(() -> new CreateTrashRequest(file).validate())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("10MB");
    }

    @Test
    @DisplayName("정확히 10MB인 파일은 검증을 통과한다")
    void 크기_경계값() {
        // given
        MockMultipartFile file = new MockMultipartFile(
            "imageFile", "exact.jpg", "image/jpeg", new byte[10 * 1024 * 1024]);

        // when & then
        assertThatCode(() -> new CreateTrashRequest(file).validate())
            .doesNotThrowAnyException();
    }
}
