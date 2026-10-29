package com.eduerp.modules.courses.internal.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Lớp cho integration test của các module dùng lớp (ghi danh, điểm, điểm danh, học phí...). Trước V31 lớp
 * mới tạo là "đang hoạt động" và dùng được cho mọi thứ; tương đương sau V31 là lớp ĐANG HỌC - nên mặc định
 * fixture đã khai giảng từ {@link #STARTED_DAYS_AGO} ngày trước, để test ghi dữ liệu cho một buổi đã qua
 * (điểm danh tuần trước...) vẫn nằm sau ngày khai giảng như ngoài đời.
 */
public final class ClassFixtures {

    private static final int STARTED_DAYS_AGO = 30;
    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private ClassFixtures() {
    }

    /** Lớp đang học, khai giảng {@value #STARTED_DAYS_AGO} ngày trước, sĩ số tối thiểu 1. */
    public static Class inProgress(Course course, String code, UUID branchId, UUID teacherId, int maxSeats) {
        var startDate = LocalDate.now(CENTER_ZONE).minusDays(STARTED_DAYS_AGO);
        var cls = new Class(course, code, branchId, teacherId, maxSeats, 1, startDate, null);
        cls.start(Instant.now().minus(Duration.ofDays(STARTED_DAYS_AGO)));
        return cls;
    }

    /** Lớp đang tuyển sinh, khai giảng hôm nay, sĩ số tối thiểu 1. */
    public static Class enrolling(Course course, String code, UUID branchId, UUID teacherId, int maxSeats) {
        return new Class(course, code, branchId, teacherId, maxSeats, 1, LocalDate.now(CENTER_ZONE), null);
    }
}
