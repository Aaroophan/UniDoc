package com.unidoc.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void mapsPopulatedSpringPageWithStableMetadata() {
        PageResponse<String> response = PageResponse.from(new PageImpl<>(
                List.of("a", "b"),
                PageRequest.of(1, 2),
                6
        ));

        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalItems()).isEqualTo(6);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();
    }

    @Test
    void emptyPageAlwaysReturnsAnEmptyItemsCollection() {
        PageResponse<String> response = PageResponse.from(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        assertThat(response.items()).isNotNull().isEmpty();
        assertThat(response.totalItems()).isZero();
        assertThat(response.totalPages()).isZero();
        assertThat(response.first()).isTrue();
        assertThat(response.last()).isTrue();
    }

    @Test
    void mappedPageUsesOneConsistentContract() {
        PageResponse<Integer> response = PageResponse.from(
                new PageImpl<>(List.of("10", "20"), PageRequest.of(0, 20), 2),
                Integer::valueOf
        );

        assertThat(response.items()).containsExactly(10, 20);
    }
}
