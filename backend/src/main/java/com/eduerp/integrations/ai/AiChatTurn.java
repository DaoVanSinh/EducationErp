package com.eduerp.integrations.ai;

/**
 * Một lượt trong hội thoại. Vai là enum chứ không phải chuỗi để không có chỗ nào viết "user"/"USER"
 * lệch nhau, và để nơi gọi không cần biết Spring AI đặt tên các kiểu message là gì.
 */
public record AiChatTurn(Role role, String content) {

    public enum Role {
        USER, ASSISTANT
    }
}
