/** Kích hoạt trình duyệt lưu một Blob thành file - cơ chế thuần, dùng chung cho mọi tính năng tải
 * file về (review finding Important #4: tải file hợp đồng). */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
