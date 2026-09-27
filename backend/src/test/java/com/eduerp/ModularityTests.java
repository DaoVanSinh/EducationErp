package com.eduerp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Biên giới module được máy kiểm tra, không dựa vào kỷ luật của người review (rule #1). */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(EduErpApplication.class);

    @Test
    void verifiesModularStructure() {
        modules.verify();
    }
}
