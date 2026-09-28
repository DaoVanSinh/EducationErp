package com.eduerp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Ranh giới module là điều kiện build, không phải chuyện nhắc nhau trong review.
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(EduErpApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }

    /**
     * Chốt danh sách module. Với {@code detection-strategy: explicitly-annotated}, một domain mới
     * thêm vào {@code modules/} mà quên {@code @ApplicationModule} sẽ im lặng không phải module —
     * nghĩa là {@code verify()} không kiểm gì cho nó và code có thể chọc thẳng vào internal của nó.
     * Test này biến cái im lặng đó thành build đỏ.
     */
    @Test
    @Timeout(30)
    void everyDomainAndIntegrationPackageIsADetectedModule() {
        var detected = modules.stream().map(ApplicationModule::getName).collect(Collectors.toSet());

        assertThat(detected).containsExactlyInAnyOrder("core", "modules.identity", "integrations.cache");
    }
}
