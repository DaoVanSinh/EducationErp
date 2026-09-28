package com.eduerp.modules.identity.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Hình dạng trang dữ liệu mà API cam kết. Không trả thẳng {@code Page} của Spring Data: JSON của nó
 * gồm cả cấu hình sort/pageable nội bộ và đã từng đổi giữa các bản, tức là một hợp đồng mà phía
 * giao diện không kiểm soát được.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
