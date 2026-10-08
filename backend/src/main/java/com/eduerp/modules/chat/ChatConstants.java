package com.eduerp.modules.chat;

import java.time.Duration;

/**
 * Hằng số dùng chung của module chat, nằm ở base package (không phải {@code internal}) vì {@code dto}
 * và {@code internal} cùng tier, cả hai cần tham chiếu - mirror {@code AcademicConstants}.
 */
public final class ChatConstants {

    private ChatConstants() {
    }

    /**
     * Vai của một lượt hội thoại trong hợp đồng HTTP. Là enum chứ không phải chuỗi để Jackson tự từ
     * chối một giá trị lạ và để không chỗ nào viết "user"/"USER" lệch nhau - mirror
     * {@code CoursesConstants.DayOfWeek} (enum ở base package vì cả {@code dto} lẫn {@code internal}
     * dùng).
     */
    public enum TurnRole {
        USER, ASSISTANT
    }

    public static final class Limits {
        private Limits() {
        }

        /**
         * Trần độ dài một câu hỏi, ký tự. Chặn một học viên dán cả một đoạn văn làm phình prompt/tốn
         * tiền (spec mục 3.2 bước 1). Kiểm ở BACKEND, không chỉ ở frontend - frontend bỏ qua được
         * bằng cách gọi API trực tiếp (Review Focus #6).
         */
        public static final int MAX_MESSAGE_LENGTH = 2000;

        /** Spec Ruling R3: "10 tin nhắn/phút/tài khoản - đủ cho một cuộc hội thoại tự nhiên, đủ thấp
         * để một tài khoản bị lộ không gây hoá đơn API bất ngờ". */
        public static final int MAX_MESSAGES_PER_MINUTE = 10;

        /** Số khóa học nhiều nhất được nhét vào groundingContext (spec mục 3.2 bước 3). Đây là trần
         * chi phí token của phần dữ liệu, không phải một giới hạn của danh mục khóa học. */
        public static final int MAX_RETRIEVED_COURSES = 5;

        /**
         * Review Focus #7: lịch sử nằm trong bộ nhớ trình duyệt nên nó LỚN DẦN suốt một phiên, và mỗi
         * lượt gửi lại toàn bộ - tiền token tăng tuyến tính theo độ dài hội thoại, và một client cố ý
         * gửi được 10.000 lượt trong một request mà rate limit (đếm số REQUEST) không thấy. Lịch sử
         * bị CẮT BỚT xuống số lượt gần nhất này, không bị từ chối: từ chối bằng 400 sẽ làm một hội
         * thoại dài tự nhiên đột ngột hỏng giữa phiên.
         */
        public static final int MAX_HISTORY_TURNS = 20;

        /**
         * Final review Critical #2: trần của MỘT LƯỢT lịch sử, KHÁC {@link #MAX_MESSAGE_LENGTH}. Một
         * lượt lịch sử có thể là câu trả lời CỦA CHÍNH CHATBOT mà client gửi lại nguyên văn ở lượt
         * sau - và câu trả lời đó bị giới hạn bởi {@code max-tokens} (hiện 1024), không phải bởi
         * {@code MAX_MESSAGE_LENGTH} (trần của câu hỏi MỚI do người dùng gõ). Tiếng Việt không hiệu
         * quả về token (nhiều token hơn cho cùng một lượng chữ), nên 1024 token hoàn toàn có thể vượt
         * 2000 ký tự. Dùng {@code MAX_MESSAGE_LENGTH} cho cả hai nghĩa là một câu trả lời dài bình
         * thường của chatbot tự khoá luôn mọi câu hỏi SAU đó trong phiên bằng 400 VALIDATION_FAILED -
         * một lỗi không nằm trong bảng lỗi của spec mục 5 và không bao giờ tự hồi phục được (tải lại
         * trang là cách DUY NHẤT thoát, và điều đó xoá luôn lịch sử - spec mục 2). Gấp bốn lần trần
         * câu hỏi là đủ rộng rãi cho một câu trả lời thật ở {@code max-tokens} hiện tại mà vẫn còn một
         * trần cứng cho một payload bị crafted thủ công.
         */
        public static final int MAX_HISTORY_TURN_LENGTH = MAX_MESSAGE_LENGTH * 4;
    }

    public static final class RateLimit {
        private RateLimit() {
        }

        /** Cửa sổ đếm của {@code ChatRateLimiter}, khớp đơn vị của
         * {@link Limits#MAX_MESSAGES_PER_MINUTE}. */
        public static final Duration WINDOW = Duration.ofMinutes(1);
    }

    /** Namespace Redis mà module này sở hữu — việc lắp key nằm ở
     * {@code integrations.cache.CacheKeyBuilder}. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }

        public static final String CHAT_RATE_LIMIT = "chat:rate";
    }
}
