import { ASSESSMENT_LIMITS } from "@/entities/assessment";
import type { QuestionDraft } from "@/modules/assessment/model/assessment-rules";

/**
 * Nhập nhanh câu hỏi bằng cách dán văn bản - định dạng Aiken mà Moodle dùng để nhập ngân hàng câu hỏi,
 * thêm dòng "GIẢI THÍCH:" tuỳ chọn. Mỗi câu cách nhau một dòng trống:
 *
 *   Thủ đô của Việt Nam là?
 *   A. Hà Nội
 *   B. Huế
 *   ĐÁP ÁN: A
 *   GIẢI THÍCH: Hà Nội là thủ đô từ năm 1010.
 *
 * Có thể đánh dấu đáp án đúng bằng dấu * trước chữ cái ("*A. Hà Nội") thay cho dòng ĐÁP ÁN.
 */
export const QUESTION_IMPORT_SAMPLE = [
  "Thủ đô của Việt Nam là?",
  "A. Hà Nội",
  "B. Huế",
  "C. Đà Nẵng",
  "ĐÁP ÁN: A",
  "GIẢI THÍCH: Hà Nội là thủ đô từ năm 1010.",
  "",
  "\"apple\" nghĩa là gì?",
  "*A. quả táo",
  "B. quả cam",
].join("\n");

export interface QuestionImportResult {
  readonly questions: readonly QuestionDraft[];
  readonly errors: readonly string[];
}

const OPTION_LINE = /^(\*)?\s*([A-F])\s*[.)]\s*(.+)$/iu;
const ANSWER_LINE = /^(?:đáp án|dap an|answer)\s*:\s*([A-F])\s*$/iu;
const EXPLANATION_LINE = /^(?:giải thích|giai thich|explanation)\s*:\s*(.*)$/iu;
const LETTER_A = "A".charCodeAt(0);

interface BlockDraft {
  content: string[];
  options: string[];
  correct: number | null;
  explanation: string | null;
}

function letterIndex(letter: string): number {
  return letter.toUpperCase().charCodeAt(0) - LETTER_A;
}

/** Đọc một dòng vào câu đang dựng; dòng không khớp mẫu nào là phần nội dung câu hỏi (trước phương án). */
function readLine(block: BlockDraft, line: string): void {
  const answer = ANSWER_LINE.exec(line);
  if (answer?.[1]) {
    block.correct = letterIndex(answer[1]);
    return;
  }
  const explanation = EXPLANATION_LINE.exec(line);
  if (explanation) {
    block.explanation = explanation[1]?.trim() || null;
    return;
  }
  const option = OPTION_LINE.exec(line);
  if (option?.[2] && option[3]) {
    if (option[1]) {
      block.correct = block.options.length;
    }
    block.options.push(option[3].trim());
    return;
  }
  if (block.options.length === 0) {
    block.content.push(line);
  } else if (block.explanation !== null) {
    block.explanation = `${block.explanation} ${line}`;
  } else {
    block.options[block.options.length - 1] += ` ${line}`;
  }
}

/** Lỗi của một câu sau khi đọc, hoặc null nếu hợp lệ. */
function blockError(block: BlockDraft): string | null {
  if (block.content.length === 0) {
    return "thiếu nội dung câu hỏi";
  }
  if (block.options.length < ASSESSMENT_LIMITS.minOptions || block.options.length > ASSESSMENT_LIMITS.maxOptions) {
    return `cần ${ASSESSMENT_LIMITS.minOptions}-${ASSESSMENT_LIMITS.maxOptions} phương án A., B., ...`;
  }
  if (block.correct === null || block.correct >= block.options.length) {
    return "thiếu dòng \"ĐÁP ÁN: <chữ cái>\" hoặc đáp án không nằm trong các phương án";
  }
  return null;
}

function toBlocks(text: string): string[][] {
  return text
    .replace(/\r\n?/g, "\n")
    .split(/\n\s*\n/)
    .map((chunk) => chunk.split("\n").map((line) => line.trim()).filter((line) => line !== ""))
    .filter((lines) => lines.length > 0);
}

export function parseQuestionText(text: string): QuestionImportResult {
  const questions: QuestionDraft[] = [];
  const errors: string[] = [];
  toBlocks(text).forEach((lines, position) => {
    const block: BlockDraft = { content: [], options: [], correct: null, explanation: null };
    lines.forEach((line) => readLine(block, line));
    const error = blockError(block);
    if (error !== null) {
      errors.push(`Câu ${position + 1}: ${error}`);
      return;
    }
    questions.push({
      content: block.content.join(" "),
      options: block.options,
      correctOptionIndex: block.correct ?? 0,
      explanation: block.explanation ?? "",
    });
  });
  return { questions, errors };
}
