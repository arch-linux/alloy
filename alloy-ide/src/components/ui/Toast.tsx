import { useState, useEffect, useCallback } from "react";
import { X, CheckCircle, AlertTriangle, Info, XCircle } from "lucide-react";

export type ToastType = "success" | "error" | "warning" | "info";

export interface ToastMessage {
  id: string;
  type: ToastType;
  message: string;
  duration?: number;
}

const typeConfig: Record<ToastType, { icon: typeof Info; color: string; bg: string; border: string }> = {
  success: {
    icon: CheckCircle,
    color: "text-green-400",
    bg: "bg-green-400/10",
    border: "border-green-400/30",
  },
  error: {
    icon: XCircle,
    color: "text-red-400",
    bg: "bg-red-400/10",
    border: "border-red-400/30",
  },
  warning: {
    icon: AlertTriangle,
    color: "text-yellow-400",
    bg: "bg-yellow-400/10",
    border: "border-yellow-400/30",
  },
  info: {
    icon: Info,
    color: "text-blue-400",
    bg: "bg-blue-400/10",
    border: "border-blue-400/30",
  },
};

// Notification history
export interface NotificationEntry {
  id: string;
  type: ToastType;
  message: string;
  timestamp: number;
}

let notificationHistory: NotificationEntry[] = [];
let historyListeners: ((entries: NotificationEntry[]) => void)[] = [];

function notifyHistoryListeners() {
  historyListeners.forEach((fn) => fn([...notificationHistory]));
}

export function getNotificationHistory(): NotificationEntry[] {
  return [...notificationHistory];
}

export function clearNotificationHistory() {
  notificationHistory = [];
  notifyHistoryListeners();
}

export function useNotificationHistory() {
  const [entries, setEntries] = useState<NotificationEntry[]>(notificationHistory);
  useEffect(() => {
    historyListeners.push(setEntries);
    return () => {
      historyListeners = historyListeners.filter((fn) => fn !== setEntries);
    };
  }, []);
  return entries;
}

// Global toast state
let toastListeners: ((toasts: ToastMessage[]) => void)[] = [];
let currentToasts: ToastMessage[] = [];

function notifyListeners() {
  toastListeners.forEach((fn) => fn([...currentToasts]));
}

export function showToast(type: ToastType, message: string, duration = 3000) {
  const id = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  const toast: ToastMessage = { id, type, message, duration };
  currentToasts = [...currentToasts, toast];
  notifyListeners();

  // Add to notification history
  notificationHistory = [
    { id, type, message, timestamp: Date.now() },
    ...notificationHistory,
  ].slice(0, 50);
  notifyHistoryListeners();

  if (duration > 0) {
    setTimeout(() => {
      dismissToast(id);
    }, duration);
  }
}

export function dismissToast(id: string) {
  currentToasts = currentToasts.filter((t) => t.id !== id);
  notifyListeners();
}

export default function ToastContainer() {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  useEffect(() => {
    toastListeners.push(setToasts);
    return () => {
      toastListeners = toastListeners.filter((fn) => fn !== setToasts);
    };
  }, []);

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-10 right-4 z-[200] flex flex-col gap-2 max-w-[360px]">
      {toasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} />
      ))}
    </div>
  );
}

function ToastItem({ toast }: { toast: ToastMessage }) {
  const config = typeConfig[toast.type];
  const Icon = config.icon;

  const handleDismiss = useCallback(() => {
    dismissToast(toast.id);
  }, [toast.id]);

  return (
    <div
      className={
        "flex items-start gap-2.5 px-3 py-2.5 rounded-lg border shadow-lg " +
        "bg-obsidian-800 " + config.border +
        " animate-in slide-in-from-right duration-200"
      }
    >
      <Icon size={16} className={config.color + " shrink-0 mt-0.5"} />
      <span className="flex-1 text-xs text-stone-200 leading-relaxed">
        {toast.message}
      </span>
      <button
        onClick={handleDismiss}
        className="text-stone-500 hover:text-stone-300 shrink-0"
      >
        <X size={13} />
      </button>
    </div>
  );
}
