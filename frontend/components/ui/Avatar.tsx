import { cn } from "@/lib/utils";

interface AvatarProps {
  name: string;
  color: string;
  size?: "xs" | "sm" | "md" | "lg";
  className?: string;
  /**
   * オンライン状態（presence）のドット。undefined なら出さない（presence 非対象の箇所）。
   * true=オンライン（緑）/ false=オフライン（グレーの中抜き）。
   */
  online?: boolean;
}

const sizeMap = {
  xs: "w-5 h-5 text-[10px] rounded",
  sm: "w-7 h-7 text-xs rounded",
  md: "w-9 h-9 text-sm rounded-md",
  lg: "w-12 h-12 text-base rounded-md",
};

// ステータスドットのサイズ。Avatar の右下に重ねる。
const dotSizeMap = {
  xs: "w-2 h-2",
  sm: "w-2.5 h-2.5",
  md: "w-3 h-3",
  lg: "w-3.5 h-3.5",
};

export function Avatar({ name, color, size = "md", className, online }: AvatarProps) {
  const initial = name.charAt(0).toUpperCase();
  const box = (
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

  // presence 対象でなければドットを足さず、従来どおり素の Avatar を返す（既存の余白に影響しない）。
  if (online === undefined) return box;

  return (
    <span className="relative inline-flex shrink-0">
      {box}
      <span
        className={cn(
          "absolute -bottom-0.5 -right-0.5 rounded-full ring-2 ring-white",
          dotSizeMap[size],
          online ? "bg-green-500" : "bg-gray-300",
        )}
        title={online ? "オンライン" : "オフライン"}
      />
    </span>
  );
}
