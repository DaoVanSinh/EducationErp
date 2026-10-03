package com.eduerp.modules.identity.internal.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RandomPasswordGeneratorTest {

    @Test
    void generatesTwelveCharacters() {
        assertThat(RandomPasswordGenerator.generate()).hasSize(12);
    }

    @Test
    void generatesDifferentValuesEachTime() {
        var values = IntStream.range(0, 20).mapToObj(i -> RandomPasswordGenerator.generate()).distinct().count();
        assertThat(values).isEqualTo(20);
    }
}
