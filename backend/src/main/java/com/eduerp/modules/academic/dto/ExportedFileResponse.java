package com.eduerp.modules.academic.dto;

/** File đã sinh xong trong bộ nhớ, chờ controller gắn {@code Content-Disposition}. */
public record ExportedFileResponse(String fileName, byte[] content) {
}
