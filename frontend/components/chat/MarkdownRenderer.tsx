"use client";

import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import rehypeSanitize from "rehype-sanitize";
import { users } from "@/lib/mock/users";

export function MarkdownRenderer({ body }: { body: string }) {
  // Replace @username with bold mention pill (preprocessing)
  const withMentions = body.replace(/@(\w+)/g, (_, name) => {
    const user = users.find((u) => u.username === name);
    if (!user) return `@${name}`;
    return `**@${user.displayName}**`;
  });
  return (
    <div className="prose-msg text-[15px] text-gray-900">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[rehypeSanitize]}
      >
        {withMentions}
      </ReactMarkdown>
    </div>
  );
}
