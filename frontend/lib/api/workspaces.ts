// ワークスペース関連の API 呼び出し。
// DTO（id は数値 BIGINT）をフロントの ViewModel（id は string）へ変換する境界を担う。
// API に無い表示専用フィールド（initial / color）はここで決定的に補完する。

import { api } from "./client";
import type { InviteDto, Page, WorkspaceDetailDto, WorkspaceDto } from "./types";
import { avatarColorFor } from "./messages";
import type { Workspace, WorkspaceMember } from "@/types";

// ワークスペースアイコンの背景色パレット（Slack 風）。id から決定的に 1 色を選ぶ。
const WORKSPACE_COLORS = [
  "#3F0E40", // aubergine
  "#007A5A", // green
  "#1264A3", // blue
  "#CD2553", // pink
  "#E8912D", // orange
  "#4A154B", // purple
];

/** id（数値）から決定的に色を選ぶ。同じ WS は常に同じ色になる。 */
function colorForId(id: number): string {
  return WORKSPACE_COLORS[Math.abs(id) % WORKSPACE_COLORS.length];
}

/** 名前の先頭 1 文字（大文字）をアイコン表示用に取り出す。 */
function initialOf(name: string): string {
  return name.trim().charAt(0).toUpperCase() || "?";
}

/** WorkspaceDto → フロントの Workspace。id を string 化し、表示用フィールドを補完する。 */
export function toWorkspace(dto: WorkspaceDto): Workspace {
  return {
    id: String(dto.id),
    name: dto.name,
    initial: initialOf(dto.name),
    color: colorForId(dto.id),
    ownerId: String(dto.ownerUserId),
  };
}

/** GET /api/workspaces 所属ワークスペース一覧。 */
export async function listWorkspaces(): Promise<Workspace[]> {
  const page = await api.get<Page<WorkspaceDto>>("/api/workspaces");
  return page.items.map(toWorkspace);
}

/** GET /api/workspaces/{wsId} ワークスペース詳細。 */
export async function getWorkspace(id: string): Promise<Workspace> {
  const dto = await api.get<WorkspaceDto>(`/api/workspaces/${Number(id)}`);
  return toWorkspace(dto);
}

/**
 * GET /api/workspaces/{wsId} の members を取り出す（詳細 API は一覧 + members を返す）。
 * id を string 化し、表示用 avatarColor を id から決定論的に補完する（DM 一覧と同方針）。
 */
export async function getWorkspaceMembers(workspaceId: string): Promise<WorkspaceMember[]> {
  const dto = await api.get<WorkspaceDetailDto>(`/api/workspaces/${Number(workspaceId)}`);
  return dto.members.map((m) => ({
    id: String(m.id),
    userId: m.userId,
    displayName: m.displayName,
    avatarColor: avatarColorFor(String(m.id)),
    role: m.role,
  }));
}

/** POST /api/workspaces ワークスペースを新規作成する。name は 1〜64 文字（バックエンド検証）。 */
export async function createWorkspace(input: {
  name: string;
  description?: string;
}): Promise<Workspace> {
  const description = input.description?.trim();
  const dto = await api.post<WorkspaceDto>("/api/workspaces", {
    name: input.name.trim(),
    // 空文字は送らず undefined にする（description は任意）。
    description: description ? description : undefined,
  });
  return toWorkspace(dto);
}

/**
 * POST /api/workspaces/{wsId}/invites 招待リンクを発行する（OWNER のみ）。
 * 平文 token と inviteUrl はこのレスポンスでのみ返る（DB はハッシュ保存）。
 * expiresInHours 既定 168h(7日) / maxUses 既定 無制限（バックエンド検証）。
 */
export async function createWorkspaceInvite(
  workspaceId: string,
  opts: { expiresInHours?: number; maxUses?: number } = {},
): Promise<InviteDto> {
  return api.post<InviteDto>(`/api/workspaces/${Number(workspaceId)}/invites`, {
    expiresInHours: opts.expiresInHours,
    maxUses: opts.maxUses,
  });
}

/**
 * POST /api/invites/{token}/accept 招待を受諾し、呼び出しユーザーを参加させる。
 * 参加したワークスペースを返す。既にメンバーなら冪等に成功。
 * 不正/削除済みは 404、期限切れ/無効化/上限超過は 410（ApiError.status で判定）。
 */
export async function acceptInvite(token: string): Promise<Workspace> {
  const dto = await api.post<WorkspaceDto>(`/api/invites/${encodeURIComponent(token)}/accept`);
  return toWorkspace(dto);
}
