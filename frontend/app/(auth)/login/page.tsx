"use client";

import { Suspense, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthContext";
import { ApiError } from "@/lib/api/problem";
import { safeNextPath } from "@/lib/utils";

// ログイン後の遷移先。/workspaces で一覧を取得し、先頭ワークスペースの先頭チャンネルへ解決する。
// 招待リンク等から ?next= が付いていればそちらへ戻す（オープンリダイレクトは safeNextPath で防ぐ）。
const AFTER_LOGIN_PATH = "/workspaces";

// useSearchParams は Suspense 境界を要求するため、フォーム本体を分離して包む。
export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const nextParam = searchParams.get("next");
  const { login } = useAuth();
  const [userId, setUserId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login({ userId, password });
      router.push(safeNextPath(nextParam, AFTER_LOGIN_PATH));
    } catch (err) {
      if (err instanceof ApiError) {
        setError(
          err.status === 401
            ? "ユーザーID またはパスワードが正しくありません"
            : err.message,
        );
      } else {
        setError("ログインに失敗しました。時間をおいて再度お試しください");
      }
      setSubmitting(false);
    }
  };

  return (
    <div className="w-full max-w-md bg-white rounded-lg shadow-md p-8">
      <h1 className="text-2xl font-bold text-center text-slack-aubergine mb-2">
        RaiseChat にログイン
      </h1>
      <p className="text-center text-sm text-gray-600 mb-6">
        RaiseTech AI ワークスペースに接続します
      </p>
      {error && (
        <div className="mb-4 px-3 py-2 bg-red-50 border border-red-200 text-red-700 text-sm rounded">
          {error}
        </div>
      )}
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label className="block text-sm font-bold mb-1">ユーザーID</label>
          <input
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            placeholder="taro"
            autoComplete="username"
            className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-gray-500 text-sm"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-bold mb-1">パスワード</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-gray-500 text-sm"
            required
          />
        </div>
        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-slack-aubergine text-white font-bold py-2.5 rounded hover:bg-slack-aubergineHover transition disabled:opacity-60"
        >
          {submitting ? "ログイン中…" : "ログイン"}
        </button>
      </form>
      <div className="mt-6 pt-6 border-t text-center text-sm text-gray-600">
        アカウントをお持ちでない方は{" "}
        <Link
          href={`/signup${nextParam ? `?next=${encodeURIComponent(nextParam)}` : ""}`}
          className="text-slack-mention font-bold hover:underline"
        >
          サインアップ
        </Link>
      </div>
    </div>
  );
}
