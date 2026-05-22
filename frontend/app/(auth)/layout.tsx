export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gradient-to-b from-purple-50 to-white flex flex-col">
      <header className="px-8 py-5">
        <div className="text-2xl font-bold text-slack-aubergine">RaiseChat</div>
      </header>
      <main className="flex-1 flex items-center justify-center px-4">{children}</main>
      <footer className="px-8 py-5 text-xs text-gray-500 text-center">
        © RaiseTech AI / RaiseChat — UI Prototype
      </footer>
    </div>
  );
}
