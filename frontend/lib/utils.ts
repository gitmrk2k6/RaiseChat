import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";
import { format, isToday, isYesterday } from "date-fns";
import { ja } from "date-fns/locale";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatMessageTime(iso: string): string {
  const d = new Date(iso);
  return format(d, "HH:mm");
}

export function formatDayLabel(iso: string): string {
  const d = new Date(iso);
  if (isToday(d)) return "今日";
  if (isYesterday(d)) return "昨日";
  return format(d, "yyyy年M月d日 (eee)", { locale: ja });
}

export function dayKey(iso: string): string {
  return format(new Date(iso), "yyyy-MM-dd");
}

/**
 * ログイン/サインアップ後のリダイレクト先（?next=）を安全に解決する。
 * オープンリダイレクト対策として、アプリ内の相対パス（"/foo"）のみ許可し、
 * "//host" や "http://..." などの外部 URL は fallback に倒す。
 */
export function safeNextPath(next: string | null, fallback: string): string {
  if (!next) return fallback;
  if (!next.startsWith("/") || next.startsWith("//")) return fallback;
  return next;
}
