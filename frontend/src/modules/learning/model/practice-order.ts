import type { AttemptResult, Question } from "@/entities/assessment";

/**
 * Thứ tự hiển thị của một lượt làm mini-task. Như Moodle ("Shuffle questions / Shuffle within questions")
 * và Azota, mỗi lượt xáo lại câu và phương án để học viên ôn kiến thức chứ không học thuộc vị trí đáp án.
 * Chỉ đổi THỨ TỰ HIỂN THỊ: đáp án gửi lên vẫn là chỉ số phương án gốc, theo thứ tự câu gốc.
 */
export interface OrderedQuestion {
  readonly question: Question;
  /** `optionOrder[vị trí hiển thị] = chỉ số phương án gốc`. */
  readonly optionOrder: readonly number[];
}

/** Bộ sinh số giả ngẫu nhiên có hạt giống (mulberry32) - cùng hạt giống cho cùng thứ tự, render ổn định. */
function seededRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4294967296;
  };
}

/** Fisher-Yates trên dãy 0..count-1. */
function shuffledIndexes(count: number, random: () => number): number[] {
  const indexes = Array.from({ length: count }, (_, index) => index);
  for (let i = indexes.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    const swap = indexes[i] as number;
    indexes[i] = indexes[j] as number;
    indexes[j] = swap;
  }
  return indexes;
}

export function orderPaper(questions: readonly Question[], seed: number): readonly OrderedQuestion[] {
  const random = seededRandom(seed);
  return shuffledIndexes(questions.length, random)
    .map((index) => questions[index])
    .filter((question): question is Question => question !== undefined)
    .map((question) => ({ question, optionOrder: shuffledIndexes(question.options.length, random) }));
}

export type QuestionReview = AttemptResult["results"][number];

/** Kết quả chấm tra theo id câu - vì thứ tự hiển thị đã khác thứ tự câu gốc. */
export function reviewsByQuestionId(result: AttemptResult | null): ReadonlyMap<string, QuestionReview> {
  return new Map((result?.results ?? []).map((review) => [review.questionId, review]));
}

/** Hạt giống mới cho một lượt làm. */
export function newPaperSeed(): number {
  return Math.floor(Math.random() * 4294967296);
}
