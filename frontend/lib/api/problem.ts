// RFC 7807 ProblemDetail に対応するエラー型と例外クラス。
// バックエンドは全エラーを application/problem+json で返すため、フロントもこの形で統一して扱う。

export interface ProblemFieldError {
  field: string;
  message: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  /** バリデーション失敗時のフィールド別メッセージ（拡張フィールド）。 */
  errors?: ProblemFieldError[];
}

/**
 * API 呼び出しが失敗したときに throw される例外。
 * status と（取得できれば）ProblemDetail 本体を保持する。
 */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }

  /** field -> message のマップ。フォームのフィールド別エラー表示に使う。 */
  get fieldErrors(): Record<string, string> {
    const map: Record<string, string> = {};
    for (const e of this.problem?.errors ?? []) {
      map[e.field] = e.message;
    }
    return map;
  }
}
