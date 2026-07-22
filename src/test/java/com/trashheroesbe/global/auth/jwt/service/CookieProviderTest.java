package com.trashheroesbe.global.auth.jwt.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.trashheroesbe.feature.user.domain.entity.User;
import com.trashheroesbe.feature.user.domain.type.Role;
import com.trashheroesbe.fixture.UserFixture;
import com.trashheroesbe.global.auth.jwt.entity.TokenType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CookieProviderTest {

    private final CookieProvider cookieProvider = new CookieProvider();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cookieProvider, "secureCookie", true);
    }

    @Test
    @DisplayName("토큰 쿠키는 HttpOnly·Secure·SameSite=None 속성과 유효 시간을 가진다")
    void 토큰_쿠키_생성() {
        // when
        Cookie cookie = cookieProvider.createTokenCookie(TokenType.ACCESS_TOKEN, "jwt-token");

        // then
        assertThat(cookie.getName()).isEqualTo("access_token");
        assertThat(cookie.getValue()).isEqualTo("jwt-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("None");
        assertThat(cookie.getMaxAge()).isEqualTo(10_800);
    }

    @Test
    @DisplayName("만료 쿠키는 값이 없고 유효 시간이 0이다")
    void 만료_쿠키_생성() {
        // when
        Cookie cookie = cookieProvider.createExpiredCookie(TokenType.ACCESS_TOKEN);

        // then
        assertThat(cookie.getName()).isEqualTo("access_token");
        assertThat(cookie.getValue()).isNull();
        assertThat(cookie.getMaxAge()).isZero();
    }

    @Test
    @DisplayName("게스트 사용자는 isMember=false 쿠키를 받는다")
    void 게스트_회원_여부() {
        // given
        User guest = User.builder().id(3L).nickname("게스트").role(Role.GUEST).build();

        // when
        Cookie cookie = cookieProvider.createRoleCheckCookie(TokenType.ACCESS_TOKEN, guest);

        // then
        assertThat(cookie.getName()).isEqualTo("isMember");
        assertThat(cookie.getValue()).isEqualTo("false");
    }

    @Test
    @DisplayName("일반 사용자는 isMember=true 쿠키를 받는다")
    void 일반_회원_여부() {
        // when
        Cookie cookie = cookieProvider.createRoleCheckCookie(
            TokenType.ACCESS_TOKEN, UserFixture.user(1L));

        // then
        assertThat(cookie.getName()).isEqualTo("isMember");
        assertThat(cookie.getValue()).isEqualTo("true");
    }
}
