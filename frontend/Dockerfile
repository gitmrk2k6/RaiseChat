# syntax=docker/dockerfile:1
# ===========================================================================
# RaiseChat frontend（Next.js 14 / Node）コンテナイメージ。
# ECS Fargate で実行する想定（infrastructure.md §12.2 / Step5）。
# next.config.mjs の output:"standalone" を前提に、マルチステージで
# .next/standalone（最小の server.js + 依存）だけを薄いランタイムに載せる。
#
# 同一オリジン配信（単一 ALB のパスルーティング）のため、ブラウザが叩く REST/WS は
# 相対 URL（= 空文字）を既定にする。別オリジン配信する場合のみ build-arg で上書きする。
#   NEXT_PUBLIC_API_BASE: REST のベース。空なら相対（/api を ALB がバックエンドへ振る）
#   NEXT_PUBLIC_WS_BASE  : SockJS の接続先。空なら相対（/ws を ALB がバックエンドへ振る）
# ===========================================================================

# --- build stage -----------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app

# 依存解決はレイヤキャッシュを効かせるため package 定義を先に入れる。
COPY package.json package-lock.json* ./
RUN npm ci

COPY . .

# NEXT_PUBLIC_* はビルド時にバンドルへ焼き込まれる。既定は空＝同一オリジン相対。
ARG NEXT_PUBLIC_API_BASE=""
ARG NEXT_PUBLIC_WS_BASE=""
ENV NEXT_PUBLIC_API_BASE=${NEXT_PUBLIC_API_BASE}
ENV NEXT_PUBLIC_WS_BASE=${NEXT_PUBLIC_WS_BASE}
ENV NEXT_TELEMETRY_DISABLED=1

RUN npm run build

# --- runtime stage ---------------------------------------------------------
FROM node:20-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
# standalone の server.js は PORT / HOSTNAME を見る。ALB ターゲット(3000)・全 IF で待受。
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# 非 root 実行（node:alpine 同梱の node ユーザー）。
USER node

# standalone 成果物 + static / public を所定の位置に配置する。
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public

EXPOSE 3000

CMD ["node", "server.js"]
