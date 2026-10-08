import { academicApi, useClassProgress } from "@/entities/academic";
import { downloadBlob } from "@/shared/lib/download-blob";
import { useCallback, useState } from "react";

const FALLBACK_FILE_NAME = "tien-trinh-hoc-tap.xlsx";

/** Toàn bộ state/query của trang Tiến trình - ui/* chỉ render (Mandate #2). Không có mutation nào:
 * tiến trình được TÍNH ở backend, không ai nhập (spec mục 1). */
export function useClassProgressController(classId: string) {
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<unknown>(null);
  const progress = useClassProgress(classId);

  const onDownload = useCallback(() => {
    setIsDownloading(true);
    setDownloadError(null);
    void academicApi
      .downloadClassProgress(classId)
      .then(({ blob, fileName }) => {
        downloadBlob(blob, fileName ?? FALLBACK_FILE_NAME);
      })
      .catch((error: unknown) => {
        setDownloadError(error);
      })
      .finally(() => {
        setIsDownloading(false);
      });
  }, [classId]);

  return { progress, onDownload, isDownloading, downloadError };
}
