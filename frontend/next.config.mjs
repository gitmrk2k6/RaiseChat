/** @type {import('next').NextConfig} */
const backendOrigin = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";

const nextConfig = {
  // 開発時は /api/* をバックエンド(:8080)へプロキシし、ブラウザからは同一オリジンに見せる。
  // これにより CORS を踏まずに REST を叩ける。NEXT_PUBLIC_API_BASE を明示した場合は
  // クライアント側が直接そのオリジンへ投げるため、この rewrites は使われない。
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
