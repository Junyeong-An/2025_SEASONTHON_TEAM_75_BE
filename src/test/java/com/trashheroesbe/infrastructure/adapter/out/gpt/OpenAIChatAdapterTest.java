package com.trashheroesbe.infrastructure.adapter.out.gpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trashheroesbe.feature.trash.domain.type.Type;
import com.trashheroesbe.feature.trash.infrastructure.TrashItemRepository;
import com.trashheroesbe.global.exception.BusinessException;
import com.trashheroesbe.global.response.type.ErrorCode;
import com.trashheroesbe.infrastructure.adapter.out.gpt.dto.SimilarResult;
import com.trashheroesbe.infrastructure.port.gpt.ImageAnalysisBundle;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

@ExtendWith(MockitoExtension.class)
class OpenAIChatAdapterTest {

    @Mock
    private TrashItemRepository trashItemRepository;

    private ChatClient chatClient;
    private OpenAIChatAdapter adapter;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        adapter = new OpenAIChatAdapter(new ObjectMapper(), chatClient, trashItemRepository);
    }

    @SuppressWarnings("unchecked")
    private void stubVisionResponse(String content) {
        given(chatClient.prompt()
            .system(anyString())
            .user(any(Consumer.class))
            .call()
            .content())
            .willReturn(content);
    }

    @SuppressWarnings("unchecked")
    private void stubVisionFailure() {
        given(chatClient.prompt()
            .system(anyString())
            .user(any(Consumer.class))
            .call()
            .content())
            .willThrow(new RuntimeException("OpenAI 호출 실패"));
    }

    private void stubTextResponse(String content) {
        given(chatClient.prompt()
            .user(anyString())
            .call()
            .content())
            .willReturn(content);
    }

    @Nested
    @DisplayName("analyzeAll")
    class AnalyzeAll {

        @Test
        @DisplayName("코드블록이 섞인 응답에서 JSON만 추출해 타입·품목·부품을 파싱한다")
        void 응답_파싱() {
            // given
            stubVisionResponse("""
                ```json
                {"type":"PET","itemName":"PET(투명 페트병)","name":"투명 페트병",
                 "parts":[{"name":"뚜껑","type":"PLASTIC"},
                          {"name":"","type":"PLASTIC"},
                          {"name":"라벨","type":"WRONG_TYPE"}]}
                ```""");

            // when
            ImageAnalysisBundle bundle = adapter.analyzeAll(new byte[]{1, 2, 3}, "image/jpeg");

            // then
            assertThat(bundle.type()).isEqualTo(Type.PET);
            assertThat(bundle.itemName()).isEqualTo("PET(투명 페트병)");
            assertThat(bundle.name()).isEqualTo("투명 페트병");
            assertThat(bundle.parts()).hasSize(1);
            assertThat(bundle.parts().get(0).name()).isEqualTo("뚜껑");
            assertThat(bundle.parts().get(0).type()).isEqualTo(Type.PLASTIC);
        }

        @Test
        @DisplayName("허용되지 않은 타입 문자열이면 UNKNOWN으로 파싱한다")
        void 잘못된_타입() {
            // given
            stubVisionResponse("{\"type\":\"ALIEN\",\"name\":\"외계 물체\"}");

            // when
            ImageAnalysisBundle bundle = adapter.analyzeAll(new byte[]{1}, "image/jpeg");

            // then
            assertThat(bundle.type()).isEqualTo(Type.UNKNOWN);
            assertThat(bundle.name()).isEqualTo("외계 물체");
            assertThat(bundle.parts()).isEmpty();
        }

        @Test
        @DisplayName("AI 호출이 실패하면 UNKNOWN 번들로 폴백한다")
        void 호출_실패_폴백() {
            // given
            stubVisionFailure();

            // when
            ImageAnalysisBundle bundle = adapter.analyzeAll(new byte[]{1}, "image/jpeg");

            // then
            assertThat(bundle.type()).isEqualTo(Type.UNKNOWN);
            assertThat(bundle.itemName()).isNull();
            assertThat(bundle.name()).isNull();
        }
    }

    @Nested
    @DisplayName("findSimilarTrashItem")
    class FindSimilarTrashItem {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "  "})
        @DisplayName("키워드가 비어 있으면 INVALID_SEARCH_KEYWORD 예외가 발생한다")
        void 빈_키워드(String invalidKeyword) {
            // when & then
            assertThatThrownBy(() ->
                adapter.findSimilarTrashItem(invalidKeyword, List.of(), List.of()))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_SEARCH_KEYWORD));
        }

        @Test
        @DisplayName("품목명이 매칭되면 itemName 결과를 반환한다")
        void 품목_매칭() {
            // given
            stubTextResponse("{\"itemName\":\"어패류 껍데기\"}");

            // when
            SimilarResult result = adapter.findSimilarTrashItem(
                "조개껍질", List.of("어패류 껍데기"), List.of(Type.FOOD_WASTE));

            // then
            assertThat(result.getItemName()).contains("어패류 껍데기");
            assertThat(result.getType()).isEmpty();
        }

        @Test
        @DisplayName("품목이 없고 타입만 매칭되면 type 결과를 반환한다")
        void 타입_매칭() {
            // given
            stubTextResponse("{\"type\":\"STYROFOAM\"}");

            // when
            SimilarResult result = adapter.findSimilarTrashItem(
                "스티로폼 박스", List.of(), List.of(Type.STYROFOAM));

            // then
            assertThat(result.getType()).contains(Type.STYROFOAM);
            assertThat(result.getItemName()).isEmpty();
        }

        @Test
        @DisplayName("NONE 응답이면 빈 결과를 반환한다")
        void 매칭_없음() {
            // given
            stubTextResponse("{\"itemName\":\"NONE\"}");

            // when
            SimilarResult result = adapter.findSimilarTrashItem(
                "정체불명", List.of("어패류 껍데기"), List.of(Type.FOOD_WASTE));

            // then
            assertThat(result.isNone()).isTrue();
        }

        @Test
        @DisplayName("AI 호출이 실패하면 ERROR_GPT_CALL 예외가 발생한다")
        void 호출_실패() {
            // given
            given(chatClient.prompt().user(anyString()).call().content())
                .willThrow(new RuntimeException("OpenAI 호출 실패"));

            // when & then
            assertThatThrownBy(() ->
                adapter.findSimilarTrashItem("조개껍질", List.of(), List.of()))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ERROR_GPT_CALL));
        }

        @Test
        @DisplayName("빈 응답이면 EMPTY_GPT_RESPONSE 예외가 발생한다")
        void 빈_응답_에러코드() {
            // given
            stubTextResponse("");

            // when & then
            assertThatThrownBy(() ->
                adapter.findSimilarTrashItem("조개껍질", List.of(), List.of()))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.EMPTY_GPT_RESPONSE));
        }

        @Test
        @DisplayName("JSON으로 파싱할 수 없는 응답이면 FAIL_PARSING_RESPONSE 예외가 발생한다")
        void 파싱_불가_응답() {
            // given
            stubTextResponse("JSON이 아닌 자유 서술 응답");

            // when & then
            assertThatThrownBy(() ->
                adapter.findSimilarTrashItem("조개껍질", List.of(), List.of()))
                .isInstanceOfSatisfying(BusinessException.class, e ->
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.FAIL_PARSING_RESPONSE));
        }
    }
}
