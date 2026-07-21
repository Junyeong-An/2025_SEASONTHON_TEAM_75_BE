package com.trashheroesbe.infrastructure.adapter.out.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3FileStorageAdapterTest {

    private static final String BUCKET = "trash-heroes-bucket";
    private static final String BASE_URL =
        "https://objectstorage.test/n/namespace/b/trash-heroes-bucket/o";

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3FileStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "bucketName", BUCKET);
        ReflectionTestUtils.setField(adapter, "publicBaseUrl", BASE_URL);
    }

    @Nested
    @DisplayName("uploadFile")
    class UploadFile {

        @Test
        @DisplayName("버킷·키·컨텐츠 타입으로 업로드하고 공개 URL을 반환한다")
        void 업로드_성공() {
            // when
            String url = adapter.uploadFile("trash/photo.jpg", "image/jpeg", new byte[]{1, 2, 3});

            // then
            ArgumentCaptor<PutObjectRequest> captor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
            verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(captor.getValue().key()).isEqualTo("trash/photo.jpg");
            assertThat(captor.getValue().contentType()).isEqualTo("image/jpeg");
            assertThat(url).isEqualTo(BASE_URL + "/trash/photo.jpg");
        }

        @Test
        @DisplayName("base URL 끝에 슬래시가 있어도 이중 슬래시 없이 URL을 만든다")
        void 슬래시_정규화() {
            // given
            ReflectionTestUtils.setField(adapter, "publicBaseUrl", BASE_URL + "/");

            // when
            String url = adapter.uploadFile("trash/photo.jpg", "image/jpeg", new byte[]{1});

            // then
            assertThat(url).isEqualTo(BASE_URL + "/trash/photo.jpg");
        }
    }

    @Nested
    @DisplayName("deleteFileByUrl")
    class DeleteFileByUrl {

        @Test
        @DisplayName("공개 base URL 형식이면 키를 추출해 삭제한다")
        void 공개_URL_삭제() {
            // when
            adapter.deleteFileByUrl(BASE_URL + "/trash/photo.jpg");

            // then
            ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(captor.getValue().key()).isEqualTo("trash/photo.jpg");
        }

        @Test
        @DisplayName("버킷명/키 형식이면 키를 추출해 삭제한다")
        void 버킷_프리픽스_삭제() {
            // when
            adapter.deleteFileByUrl(BUCKET + "/trash/photo.jpg");

            // then
            ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertThat(captor.getValue().key()).isEqualTo("trash/photo.jpg");
        }

        @Test
        @DisplayName("다른 호스트라도 경로가 버킷으로 시작하면 키를 추출해 삭제한다")
        void 경로_버킷_삭제() {
            // when
            adapter.deleteFileByUrl("https://other-host.test/" + BUCKET + "/trash/photo.jpg");

            // then
            ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
            verify(s3Client).deleteObject(captor.capture());
            assertThat(captor.getValue().key()).isEqualTo("trash/photo.jpg");
        }

        @Test
        @DisplayName("허용되지 않은 URL이면 예외가 발생하고 삭제 요청을 보내지 않는다")
        void 허용되지_않은_URL() {
            // when & then
            assertThatThrownBy(() ->
                adapter.deleteFileByUrl("https://evil.test/other-bucket/photo.jpg"))
                .isInstanceOf(IllegalArgumentException.class);
            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "  "})
        @DisplayName("URL이 비어 있으면 예외가 발생한다")
        void 빈_URL(String invalidUrl) {
            // when & then
            assertThatThrownBy(() -> adapter.deleteFileByUrl(invalidUrl))
                .isInstanceOf(IllegalArgumentException.class);
            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }
    }
}
