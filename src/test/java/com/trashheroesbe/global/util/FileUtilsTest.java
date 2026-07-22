package com.trashheroesbe.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FileUtilsTest {

    private static final String PREFIX = "trash/";

    @Test
    @DisplayName("prefix + 타임스탬프_UUID8자리 + 확장자 형식의 키를 생성한다")
    void 키_형식() {
        // given
        String originalFileName = "photo.jpg";

        // when
        String key = FileUtils.generateStoredKey(originalFileName, PREFIX);

        // then
        assertThat(key).matches("trash/\\d{8}_\\d{6}_[0-9a-f]{8}\\.jpg");
    }

    @Test
    @DisplayName("원본 파일명의 확장자를 보존한다")
    void 확장자_보존() {
        // when
        String upperCaseKey = FileUtils.generateStoredKey("image.PNG", PREFIX);
        String multiDotKey = FileUtils.generateStoredKey("archive.tar.gz", PREFIX);

        // then
        assertThat(upperCaseKey).endsWith(".PNG");
        assertThat(multiDotKey).endsWith(".gz");
    }

    @Test
    @DisplayName("확장자가 없는 파일명은 확장자 없이 키를 생성한다")
    void 확장자_없음() {
        // when
        String key = FileUtils.generateStoredKey("noextension", PREFIX);

        // then
        assertThat(key).matches("trash/\\d{8}_\\d{6}_[0-9a-f]{8}");
    }

    @Test
    @DisplayName("점으로 시작하는 파일명은 확장자로 취급하지 않는다")
    void 숨김_파일() {
        // when
        String key = FileUtils.generateStoredKey(".hidden", PREFIX);

        // then
        assertThat(key).doesNotContain(".hidden");
        assertThat(key).matches("trash/\\d{8}_\\d{6}_[0-9a-f]{8}");
    }

    @Test
    @DisplayName("같은 파일명으로 생성해도 매번 다른 키가 나온다")
    void 키_유일성() {
        // given
        String originalFileName = "photo.jpg";

        // when
        String first = FileUtils.generateStoredKey(originalFileName, PREFIX);
        String second = FileUtils.generateStoredKey(originalFileName, PREFIX);

        // then
        assertThat(first).isNotEqualTo(second);
    }
}
