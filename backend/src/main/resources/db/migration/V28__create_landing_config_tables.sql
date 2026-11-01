-- =============================================================================
-- V28: Cấu hình thông tin trung tâm, nội dung Landing Page và danh sách đăng ký tư vấn
-- =============================================================================

CREATE TABLE landing_center_configs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                VARCHAR(32)  NOT NULL UNIQUE,
    brand_name          VARCHAR(160) NOT NULL,
    brand_subtitle      VARCHAR(255) NOT NULL,
    tagline             VARCHAR(255) NOT NULL,
    hero_badge          VARCHAR(255) NOT NULL,
    hero_title          VARCHAR(255) NOT NULL,
    hero_highlight      VARCHAR(255) NOT NULL,
    hero_description    TEXT         NOT NULL,
    hotline             VARCHAR(32)  NOT NULL,
    hotline_display     VARCHAR(64)  NOT NULL,
    email               VARCHAR(160) NOT NULL,
    work_time           VARCHAR(160) NOT NULL,
    default_address     VARCHAR(255) NOT NULL,
    zalo_url            VARCHAR(255) NOT NULL,
    messenger_url       VARCHAR(255) NOT NULL,
    footer_description  TEXT         NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE landing_roadmap_steps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    step_code       VARCHAR(16)  NOT NULL,
    badge_text      VARCHAR(160) NOT NULL,
    sub_tag         VARCHAR(160) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    duration        VARCHAR(160) NOT NULL,
    target          VARCHAR(255) NOT NULL,
    color_theme     VARCHAR(32)  NOT NULL,
    features_text   TEXT         NOT NULL,
    display_order   INT          NOT NULL
);

CREATE INDEX idx_landing_roadmap_steps_order ON landing_roadmap_steps (display_order ASC);

CREATE TABLE landing_features (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    icon_key        VARCHAR(32)  NOT NULL,
    variant         VARCHAR(32)  NOT NULL,
    subtitle        VARCHAR(160) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT         NOT NULL,
    display_order   INT          NOT NULL
);

CREATE INDEX idx_landing_features_order ON landing_features (display_order ASC);

CREATE TABLE landing_faqs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question        VARCHAR(500) NOT NULL,
    answer          TEXT         NOT NULL,
    display_order   INT          NOT NULL
);

CREATE INDEX idx_landing_faqs_order ON landing_faqs (display_order ASC);

CREATE TABLE landing_consultations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name           VARCHAR(160) NOT NULL,
    phone               VARCHAR(32)  NOT NULL,
    email               VARCHAR(160) NULL,
    target_course_id    UUID         NULL REFERENCES courses(id) ON DELETE SET NULL,
    target_branch_id    UUID         NULL REFERENCES branches(id) ON DELETE SET NULL,
    notes               TEXT         NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'NEW',
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_landing_consultations_created ON landing_consultations (created_at DESC);

INSERT INTO landing_center_configs (
    code,
    brand_name,
    brand_subtitle,
    tagline,
    hero_badge,
    hero_title,
    hero_highlight,
    hero_description,
    hotline,
    hotline_display,
    email,
    work_time,
    default_address,
    zalo_url,
    messenger_url,
    footer_description
) VALUES (
    'DEFAULT',
    'DS Academy',
    'Nền Tảng Đào Tạo & Phát Triển Năng Lực Toàn Diện',
    'Kiến tạo nền tảng vững chắc – Bứt phá kết quả đầu ra',
    'TUYỂN SINH CÁC LỚP ĐÀO TẠO MỚI NHẤT',
    'Lộ Trình Học Tập Bài Bản &',
    'Cam Kết Chất Lượng Đầu Ra',
    'Khám phá các khoá học đang tuyển sinh, tra cứu lịch khai giảng tại từng cơ sở, đội ngũ giảng viên giàu kinh nghiệm và tính toán ngay mức ưu đãi học phí khi đăng ký theo gói lộ trình.',
    '0869724615',
    '0387 367 162',
    'tuyensinh@hocvien.edu.vn',
    'Thứ 2 - Thứ 7: 08:00 - 20:30',
    '23B Nguyễn Văn Thủ, Phường Đa Kao, Quận 1, TP. Hồ Chí Minh',
    'https://zalo.me/0869724615',
    'https://m.me/hocvien',
    'Đơn vị đào tạo uy tín với chương trình học chuẩn hoá, lộ trình cá nhân hoá theo năng lực, cơ sở vật chất hiện đại và đội ngũ giảng viên tận tâm đồng hành cùng học viên.'
);

INSERT INTO landing_roadmap_steps (
    step_code, badge_text, sub_tag, title, duration, target, color_theme, features_text, display_order
) VALUES
(
    '01',
    'Giai Đoạn Nền Tảng',
    'Định hướng & Xếp lớp đầu vào',
    'Đánh Giá Năng Lực & Xây Nền Cốt Lõi',
    '8 – 12 tuần (24 – 36 buổi)',
    'Nắm vững kiến thức nền tảng & phương pháp học chuẩn',
    'blue',
    E'Kiểm tra năng lực đầu vào 1:1 và xếp lớp phù hợp tại cơ sở gần nhất\nGiáo trình chuẩn hoá với hệ thống bài đánh giá định kỳ rõ ràng\nTheo dõi sát sao chuyên cần và tiến độ từng buổi học',
    1
),
(
    '02',
    'Giai Đoạn Tăng Tốc',
    'Phát triển kỹ năng trọng tâm',
    'Bứt Phá Kỹ Năng & Chuyên Đề Trọng Tâm',
    '10 – 16 tuần (30 – 48 buổi)',
    'Hoàn thiện kỹ năng thực hành & bài tập chuyên đề',
    'rose',
    E'Rèn luyện chuyên sâu theo từng học phần cùng đội ngũ giảng viên giàu kinh nghiệm\nKiểm tra định kỳ giữa khoá & cuối khoá kèm nhận xét chi tiết\nHưởng ưu đãi giảm học phí hấp dẫn khi đăng ký theo gói lộ trình Combo',
    2
),
(
    '03',
    'Giai Đoạn Thực Chiến',
    'Tổng ôn & Đánh giá chuyên sâu',
    'Luyện Đề Thực Chiến & Kèm Cặp 1:1',
    '8 – 12 tuần (24 – 36 buổi)',
    'Đạt chuẩn điểm số mục tiêu & kỹ năng ứng dụng thực tế',
    'purple',
    E'Giải bài tập thực chiến cường độ cao, chữa bài và phân tích lỗi sai chi tiết\nBổ trợ kiến thức 1:1 dựa trên kết quả học tập thực tế của từng học viên\nĐánh giá tổng kết toàn diện trước khi bước vào kỳ thi cuối khoá',
    3
),
(
    '04',
    'Hoàn Tất & Chứng Nhận',
    'Tốt nghiệp & Lộ trình nâng cao',
    'Chứng Nhận Hoàn Thành & Đồng Hành Dài Hạn',
    'Trọn lộ trình & hậu đào tạo',
    'Hoàn tất chương trình & sẵn sàng ứng dụng thực tế',
    'amber',
    E'Cấp chứng nhận hoàn thành khoá học và bảng tổng kết kết quả học tập\nTư vấn định hướng các chương trình nâng cao với ưu đãi dành cho học viên cũ\nĐồng hành hỗ trợ học thuật và kết nối cộng đồng học viên lâu dài',
    4
);

INSERT INTO landing_features (
    icon_key, variant, subtitle, title, description, display_order
) VALUES
(
    'wallet',
    'blue',
    'Học phí & Ưu đãi minh bạch',
    'Chính sách học phí rõ ràng, ưu đãi theo lộ trình',
    'Mọi mức học phí, thời lượng buổi học và chính sách giảm giá khi đăng ký theo gói Combo đều được công khai minh bạch, cam kết không phát sinh chi phí ẩn.',
    1
),
(
    'graduation',
    'rose',
    'Đội ngũ giảng viên',
    'Giảng viên chuyên môn cao, tận tâm kèm cặp',
    '100% lớp học được trực tiếp giảng dạy bởi đội ngũ giảng viên giàu kinh nghiệm, theo dõi sát sao sự tiến bộ của từng học viên.',
    2
),
(
    'sparkles',
    'emerald',
    'Đánh giá năng lực định kỳ',
    'Theo dõi tiến độ & kết quả học tập rõ ràng',
    'Hệ thống bài kiểm tra định kỳ được phân bổ hợp lý theo từng giai đoạn, giúp học viên và phụ huynh nắm bắt chính xác kết quả học tập.',
    3
),
(
    'shield',
    'purple',
    'Hệ thống đa cơ sở',
    'Linh hoạt chọn cơ sở và khung giờ học',
    'Lịch khai giảng đa dạng các ca Sáng - Chiều - Tối và cuối tuần tại nhiều chi nhánh, thuận tiện cho học sinh, sinh viên và người đi làm.',
    4
),
(
    'clock',
    'amber',
    'Lộ trình cá nhân hoá 1:1',
    'Thiết kế lộ trình sát mục tiêu & năng lực',
    'Tư vấn viên kiểm tra trình độ đầu vào và xây dựng kế hoạch học tập tối ưu dựa trên thời gian biểu và mục tiêu riêng của từng học viên.',
    5
),
(
    'award',
    'cyan',
    'Thanh toán linh hoạt',
    'Hỗ trợ đóng học phí linh hoạt & trực tuyến',
    'Hỗ trợ nhiều phương thức thanh toán tiện lợi qua cổng thanh toán trực tuyến, chuyển khoản hoặc đóng trực tiếp tại quầy tư vấn.',
    6
);

INSERT INTO landing_faqs (
    question, answer, display_order
) VALUES
(
    'Làm thế nào để chọn được khoá học và lớp học phù hợp với trình độ?',
    'Bạn có thể xem chi tiết nội dung, thời lượng và lịch học của từng lớp ngay trên trang, hoặc đăng ký tư vấn để được kiểm tra trình độ đầu vào 1:1 hoàn toàn miễn phí trước khi xếp lớp.',
    1
),
(
    'Làm thế nào để được hưởng ưu đãi giảm học phí khi đăng ký Combo?',
    'Khi đăng ký từ 2 khoá học trở lên trong cùng lộ trình đào tạo, bạn sẽ được áp dụng ngay mức chiết khấu phần trăm học phí tương ứng trên công cụ dự toán học phí.',
    2
),
(
    'Trong quá trình học, kết quả học tập được đánh giá như thế nào?',
    'Mỗi khoá học đều có các cột mốc đánh giá định kỳ (chuyên cần, bài kiểm tra giữa khoá, cuối khoá) với trọng số rõ ràng giúp đảm bảo chất lượng đầu ra.',
    3
),
(
    'Trung tâm có hỗ trợ đóng học phí theo từng đợt hoặc thanh toán online không?',
    'Có. Học viên và phụ huynh có thể lựa chọn thanh toán trực tuyến nhanh chóng qua VNPay / MoMo, chuyển khoản ngân hàng hoặc đóng trực tiếp tại cơ sở học tập.',
    4
),
(
    'Sau khi gửi thông tin đăng ký tư vấn, bao lâu tôi sẽ nhận được phản hồi?',
    'Bộ phận Tư vấn Tuyển sinh sẽ liên hệ lại với bạn qua số điện thoại hoặc Zalo trong vòng 15–30 phút trong khung giờ làm việc để hỗ trợ giải đáp và xếp lịch kiểm tra đầu vào miễn phí.',
    5
);
