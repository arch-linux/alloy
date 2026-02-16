import { useEffect, useRef } from "react";
import { AlertTriangle } from "lucide-react";

interface ConfirmDialogProps {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: "default" | "danger";
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({
  title,
  message,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  variant = "default",
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    confirmRef.current?.focus();
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCancel();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [onCancel]);

  const isDanger = variant === "danger";

  return (
    <div
      className="fixed inset-0 z-[110] flex items-center justify-center"
      style={{ backgroundColor: "rgba(6, 6, 10, 0.8)" }}
      onClick={onCancel}
    >
      <div
        className="w-[400px] max-w-[90vw] rounded-lg border border-obsidian-600 bg-obsidian-800 shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-start gap-3 px-5 pt-5 pb-3">
          {isDanger && (
            <div className="mt-0.5 shrink-0 rounded-full bg-molten/15 p-2">
              <AlertTriangle size={18} className="text-molten" />
            </div>
          )}
          <div className="flex-1 min-w-0">
            <h3 className="text-sm font-semibold text-stone-100 font-heading">
              {title}
            </h3>
            <p className="mt-1.5 text-xs text-stone-400 leading-relaxed">
              {message}
            </p>
          </div>
        </div>
        <div className="flex justify-end gap-2 px-5 py-4 bg-obsidian-900/50">
          <button
            onClick={onCancel}
            className="rounded-md px-3 py-1.5 text-xs font-medium text-stone-300 bg-obsidian-700 hover:bg-obsidian-600 border border-obsidian-600 transition-colors"
          >
            {cancelLabel}
          </button>
          <button
            ref={confirmRef}
            onClick={onConfirm}
            className={
              "rounded-md px-3 py-1.5 text-xs font-medium transition-colors " +
              (isDanger
                ? "bg-molten/15 text-molten border border-molten/30 hover:bg-molten/25"
                : "bg-ember text-obsidian-950 hover:bg-ember-light")
            }
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

// Global confirm dialog system
type ConfirmCallback = (result: boolean) => void;
let showConfirmFn: ((opts: ConfirmOptions) => void) | null = null;

interface ConfirmOptions {
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: "default" | "danger";
}

export function showConfirm(opts: ConfirmOptions): Promise<boolean> {
  return new Promise((resolve) => {
    if (showConfirmFn) {
      showConfirmFn({ ...opts });
      pendingResolve = resolve;
    } else {
      // Fallback to native
      resolve(window.confirm(opts.message));
    }
  });
}

let pendingResolve: ConfirmCallback | null = null;

export function registerConfirmHandler(fn: (opts: ConfirmOptions) => void) {
  showConfirmFn = fn;
}

export function resolveConfirm(result: boolean) {
  if (pendingResolve) {
    pendingResolve(result);
    pendingResolve = null;
  }
}
