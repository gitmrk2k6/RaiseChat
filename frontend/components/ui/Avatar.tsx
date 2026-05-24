import { cn } from "@/lib/utils";

interface AvatarProps {
  name: string;
  color: string;
  size?: "xs" | "sm" | "md" | "lg";
  className?: string;
}

const sizeMap = {
  xs: "w-5 h-5 text-[10px] rounded",
  sm: "w-7 h-7 text-xs rounded",
  md: "w-9 h-9 text-sm rounded-md",
  lg: "w-12 h-12 text-base rounded-md",
};

export function Avatar({ name, color, size = "md", className }: AvatarProps) {
  const initial = name.charAt(0).toUpperCase();
  return (
    <div
      className={cn(
        "flex items-center justify-center font-bold text-white select-none shrink-0",
        sizeMap[size],
        className,
      )}
      style={{ backgroundColor: color }}
    >
      {initial}
    </div>
  );
}
