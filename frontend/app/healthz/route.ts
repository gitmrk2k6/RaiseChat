// ALB ターゲットグループのヘルスチェック用エンドポイント（GET /healthz → 200）。
// ルート "/" は /login へリダイレクト（3xx）するためヘルスチェックに使いにくく、
// アプリ状態に依存しない軽量な 200 を返す専用パスを用意する（infrastructure.md §12.2 / Step5）。
// force-static で静的応答にし、standalone ランタイムでも余計な処理なく即 200 を返す。
export const dynamic = "force-static";

export function GET() {
  return new Response("ok", {
    status: 200,
    headers: { "content-type": "text/plain; charset=utf-8" },
  });
}
