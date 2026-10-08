package com.eduerp.modules.chat.dto;

/**
 * Chỉ MỘT field, và đó là toàn bộ hợp đồng ra (spec mục 3.2). Không trả kèm danh sách khóa học đã
 * truy xuất: giao diện không có chỗ hiển thị nó, và trả về nghĩa là hứa một hợp đồng phải giữ.
 * Không trả kèm id hội thoại: không có hội thoại nào được lưu (spec mục 2).
 */
public record AskChatbotResponse(String reply) {
}
