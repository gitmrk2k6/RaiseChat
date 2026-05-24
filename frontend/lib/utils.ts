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
