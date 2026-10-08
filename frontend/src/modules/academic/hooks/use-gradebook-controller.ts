import { academicApi, useGradebook, type StudentGradesRow } from "@/entities/academic";
import { downloadBlob } from "@/shared/lib/download-blob";
import type { GradeColumn } from "@/shared/api/schemas";
import { useCallback, useState } from "react";

export interface EditingCell {
  readonly row: StudentGradesRow;
  readonly column: GradeColumn;
}

const FALLBACK_FILE_NAME = "bang-diem.xlsx";

/** Toàn bộ state/query/mutation của trang Sổ điểm - ui/* chỉ render (Mandate #2). */
export function useGradebookController(classId: string) {
  const [editing, setEditing] = useState<EditingCell | null>(null);
  const [isDownloading, setIsDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<unknown>(null);
  const gradebook = useGradebook(classId);

  const startEditing = useCallback((row: StudentGradesRow, column: GradeColumn) => {
    setEditing({ row, column });
  }, []);

  const stopEditing = useCallback(() => setEditing(null), []);

  const onDownload = useCallback(() => {
    setIsDownloading(true);
    setDownloadError(null);
    void academicApi
      .downloadGradebook(classId)
      .then(({ blob, fileName }) => {
        downloadBlob(blob, fileName ?? FALLBACK_FILE_NAME);
      })
      .catch((error: unknown) => {
        // 403 ACADEMIC_NOT_ASSIGNED_TO_CLASS là nguồn sự thật về quyền (spec mục 10) - hiện nguyên
        // lỗi của backend thay vì im lặng.
        setDownloadError(error);
      })
      .finally(() => {
        setIsDownloading(false);
      });
  }, [classId]);

  return {
    gradebook,
    editing,
    startEditing,
    stopEditing,
    onDownload,
    isDownloading,
    downloadError,
  };
}
