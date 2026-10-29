package com.eduerp.modules.assessment.dto;

import java.util.UUID;

/** Id của bài tập / mini-task vừa tạo - frontend dùng để làm mới đúng bảng của lớp. */
public record CreatedResponse(UUID id) {
}
