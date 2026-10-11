package com.eduerp.modules.academic.dto;

/** File đã sinh xong trong bộ nhớ, chờ controller gắn {@code Content-Disposition} - mirror cách
 * {@code DownloadContractFile} trả về cặp tên file + nội dung. */
public record ExportedFileResponse(String fileName, byte[] content) {
}
