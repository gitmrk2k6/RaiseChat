import { redirect } from "next/navigation";
import { getChannelsByWorkspace } from "@/lib/mock/channels";

export default function WorkspaceIndex({ params }: { params: { workspaceId: string } }) {
  const channels = getChannelsByWorkspace(params.workspaceId);
  const first = channels[0]?.id ?? "ch-general";
  redirect(`/workspaces/${params.workspaceId}/channels/${first}`);
}
