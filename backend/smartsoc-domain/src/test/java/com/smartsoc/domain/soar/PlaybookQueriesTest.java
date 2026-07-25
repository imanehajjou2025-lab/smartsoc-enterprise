package com.smartsoc.domain.soar;

import com.smartsoc.domain.common.PageQuery;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Les deux critères de recherche du module SOAR défendent la même règle : une page absente en prend une par défaut. */
class PlaybookQueriesTest {

    @Test
    void playbookQueryDefaultsToPageZeroSizeTwentyFiveWhenNull() {
        PlaybookQuery query = new PlaybookQuery("ransomware", false, null);
        assertThat(query.page()).isEqualTo(PageQuery.of(0, 25));
    }

    @Test
    void playbookExecutionQueryDefaultsToPageZeroSizeTwentyFiveWhenNull() {
        PlaybookExecutionQuery query = new PlaybookExecutionQuery(UUID.randomUUID(), null);
        assertThat(query.page()).isEqualTo(PageQuery.of(0, 25));
    }
}
