import { WorkspaceRail } from "@/components/layout/WorkspaceRail";
import { Sidebar } from "@/components/layout/Sidebar";
import { TopBar } from "@/components/layout/TopBar";
import { AuthGuard } from "@/components/auth/AuthGuard";

export default function WorkspaceLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthGuard>
      <div className="h-screen flex flex-col bg-slack-aubergineDark">
        <TopBar />
        <div className="flex-1 flex min-h-0">
          <WorkspaceRail />
          <Sidebar />
          <div className="flex-1 flex bg-white min-w-0">{children}</div>
        </div>
      </div>
    </AuthGuard>
  );
}
