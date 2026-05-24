import { ButtonHTMLAttributes, forwardRef } from "react";
import { cn } from "@/lib/utils";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "danger" | "ghost";
}

const variantMap = {
  primary: "bg-slack-accent text-white hover:bg-emerald-700 disabled:bg-emerald-300",
  secondary: "bg-white text-slack-aubergine border border-slack-aubergine hover:bg-purple-50",
  danger: "bg-red-600 text-white hover:bg-red-700",
  ghost: "text-gray-700 hover:bg-gray-100",
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ variant = "primary", className, ...props }, ref) => (
    <button
      ref={ref}
      className={cn(
        "px-4 py-1.5 rounded-md text-sm font-bold transition-colors disabled:cursor-not-allowed",
        variantMap[variant],
        className,
      )}
      {...props}
    />
  ),
);
Button.displayName = "Button";
