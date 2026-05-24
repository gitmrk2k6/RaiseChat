import type { Workspace } from "@/types";

export const workspaces: Workspace[] = [
  {
    id: "ws-1",
    name: "RaiseTech AI",
    initial: "R",
    color: "#3F0E40",
    ownerId: "user-1",
  },
  {
    id: "ws-2",
    name: "Side Project",
    initial: "S",
    color: "#007A5A",
    ownerId: "user-1",
  },
];

export const currentWorkspaceId = "ws-1";

export const getWorkspace = (id: string): Workspace =>
  workspaces.find((w) => w.id === id) ?? workspaces[0];
