"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("kkd28mr@gmail.com");
  const [password, setPassword] = useState("password");

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    router.push("/workspaces/ws-1/channels/ch-general");
  };

  return (
    <div className="w-full max-w-md bg-white rounded-lg shadow-md p-8">
      <h1 className="text-2xl font-bold text-center text-slack-aubergine mb-2">
        RaiseChat にログイン
      </h1>
      <p className="text-center text-sm text-gray-600 mb-6">
        RaiseTech AI ワークスペースに接続します
      </p>
      <form onSubmit={submit} className="space-y-4">
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
          />
        </div>
        <button
          type="submit"
          className="w-full bg-slack-aubergine text-white font-bold py-2.5 rounded hover:bg-slack-aubergineHover transition"
        >
          ログイン
        </button>
      </form>
      <p className="text-xs text-gray-500 text-center mt-4">
        ※ プロトタイプのため、任意の値で先に進めます
      </p>
      <div className="mt-6 pt-6 border-t text-center text-sm text-gray-600">
        アカウントをお持ちでない方は{" "}
        <Link href="/signup" className="text-slack-mention font-bold hover:underline">
          サインアップ
        </Link>
      </div>
    </div>
  );
}
