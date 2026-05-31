"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth/AuthContext";
import { ApiError } from "@/lib/api/problem";

const AFTER_SIGNUP_PATH = "/workspaces/ws-1/channels/ch-general";

export default function SignupPage() {
  const router = useRouter();
  const { signup } = useAuth();
  const [displayName, setDisplayName] = useState("");
  const [userId, setUserId] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setFieldErrors({});
    setSubmitting(true);
    try {
      await signup({ userId, displayName, password });
      router.push(AFTER_SIGNUP_PATH);
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors);
        // フィールド別エラーが無いときだけ全体メッセージを出す（409 重複など）。
        if (Object.keys(err.fieldErrors).length === 0) {
          setError(
            err.status === 409
              ? "このユーザーID はすでに使われています"
              : err.message,
          );
        }
      } else {
        setError("アカウント作成に失敗しました。時間をおいて再度お試しください");
      }
      setSubmitting(false);
    }
  };

  return (
    <div className="w-full max-w-md bg-white rounded-lg shadow-md p-8">
      <h1 className="text-2xl font-bold text-center text-slack-aubergine mb-2">
        新規アカウント作成
      </h1>
      <p className="text-center text-sm text-gray-600 mb-6">RaiseChat へようこそ！</p>
      {error && (
        <div className="mb-4 px-3 py-2 bg-red-50 border border-red-200 text-red-700 text-sm rounded">
          {error}
        </div>
      )}
      <form onSubmit={submit} className="space-y-4">
        <div>
          <label className="block text-sm font-bold mb-1">表示名</label>
          <input
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            placeholder="山田 太郎"
            className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-gray-500 text-sm"
            required
          />
          {fieldErrors.displayName && (
            <p className="mt-1 text-xs text-red-600">{fieldErrors.displayName}</p>
          )}
        </div>
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
          <p className="mt-1 text-xs text-gray-500">
            半角英数字・ハイフン・アンダースコア 3〜32 文字
          </p>
          {fieldErrors.userId && (
            <p className="mt-1 text-xs text-red-600">{fieldErrors.userId}</p>
          )}
        </div>
        <div>
          <label className="block text-sm font-bold mb-1">パスワード</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="new-password"
            className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-gray-500 text-sm"
            required
            minLength={8}
          />
          <p className="mt-1 text-xs text-gray-500">8 文字以上</p>
          {fieldErrors.password && (
            <p className="mt-1 text-xs text-red-600">{fieldErrors.password}</p>
          )}
        </div>
        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-slack-aubergine text-white font-bold py-2.5 rounded hover:bg-slack-aubergineHover transition disabled:opacity-60"
        >
          {submitting ? "作成中…" : "アカウントを作成"}
        </button>
      </form>
      <div className="mt-6 pt-6 border-t text-center text-sm text-gray-600">
        既にアカウントをお持ちの方は{" "}
        <Link href="/login" className="text-slack-mention font-bold hover:underline">
          ログイン
        </Link>
      </div>
    </div>
  );
}
