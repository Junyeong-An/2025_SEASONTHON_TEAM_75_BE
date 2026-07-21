package com.trashheroesbe;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("로컬 MySQL 등 외부 인프라 없이는 컨텍스트 로드가 불가능. Testcontainers 기반 통합 테스트 환경 구성 시 활성화 예정")
@SpringBootTest
class TrashHeroesBeApplicationTests {

    @Test
    void contextLoads() {
    }

}
