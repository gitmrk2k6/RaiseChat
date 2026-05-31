// ワークスペース関連の API 呼び出し。
// DTO（id は数値 BIGINT）をフロントの ViewModel（id は string）へ変換する境界を担う。
// API に無い表示専用フィールド（initial / color）はここで決定的に補完する。

import { api } from "./client";
import type { Page, WorkspaceDto } from "./types";
import type { Workspace } from "@/types";

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
