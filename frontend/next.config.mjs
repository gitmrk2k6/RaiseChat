/** @type {import('next').NextConfig} */
const backendOrigin = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig = {
  // ECS Fargate で Node ランタイムをそのまま動かすため standalone 出力にする（infrastructure.md §12.2 / Step5）。
  // .next/standalone に最小の server.js と依存だけがまとまり、Dockerfile で薄いランタイムイメージに載せられる。
  // 本番は単一 ALB のパスルーティング（default→フロント / /api・/ws→バックエンド）で同一オリジン配信するため、
  // ブラウザは相対 URL（NEXT_PUBLIC_API_BASE 空）で REST/WS を叩け、CORS を踏まない。
  output: "standalone",

  // 開発時は /api/* をバックエンド(:8080)へプロキシし、ブラウザからは同一オリジンに見せる。
  // これにより CORS を踏まずに REST を叩ける。NEXT_PUBLIC_API_BASE を明示した場合は
  // クライアント側が直接そのオリジンへ投げるため、この rewrites は使われない。
  // 本番（ALB 配下）では /api は ALB がバックエンドへ振るためフロントの Node には届かず、この rewrites は不使用。
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendOrigin}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
