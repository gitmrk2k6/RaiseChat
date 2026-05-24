"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";

export default function SignupPage() {
  const router = useRouter();
  const [displayName, setDisplayName] = useState("");
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    router.push("/workspaces/ws-1/channels/ch-general");
  };

  return (
    <div className="w-full max-w-md bg-white rounded-lg shadow-md p-8">
      <h1 className="text-2xl font-bold text-center text-slack-aubergine mb-2">
        新規アカウント作成
      </h1>
      <p className="text-center text-sm text-gray-600 mb-6">
        RaiseChat へようこそ！
      </p>
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
        </div>
        <div>
          <label className="block text-sm font-bold mb-1">ユーザー名</label>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="taro"
            className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-gray-500 text-sm"
            required
          />
        </div>
        <div>
          <label className="block text-sm font-bold mb-1">メールアドレス</label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
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
            className="w-full px-3 py-2 border border-gray-300 rounded outline-none focus:border-gray-500 text-sm"
            required
            minLength={1}
          />
        </div>
        <button
          type="submit"
          className="w-full bg-slack-aubergine text-white font-bold py-2.5 rounded hover:bg-slack-aubergineHover transition"
        >
          アカウントを作成
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
