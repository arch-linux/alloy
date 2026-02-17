import { useEffect, useRef } from "react";
import { X, XCircle, Files, Copy, FolderOpen, Columns2, Rows2, Pin, PinOff } from "lucide-react";
import { useStore } from "../../lib/store";
import { showToast } from "../ui/Toast";

interface TabContextMenuProps {
  x: number;
  y: number;
  filePath: string;
  onClose: () => void;
}

export default function TabContextMenu({ x, y, filePath, onClose }: TabContextMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    const keyHandler = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("mousedown", handler);
    document.addEventListener("keydown", keyHandler);
    return () => {
      document.removeEventListener("mousedown", handler);
      document.removeEventListener("keydown", keyHandler);
    };
  }, [onClose]);

  // Adjust position if menu would overflow viewport
  const style: React.CSSProperties = {
    position: "fixed",
    left: x,
    top: y,
    zIndex: 200,
  };

  const close = () => {
    useStore.getState().closeFile(filePath);
    onClose();
  };

  const closeOthers = () => {
    const { openFiles, closeFile } = useStore.getState();
    openFiles.forEach((f) => {
      if (f.path !== filePath) closeFile(f.path);
    });
    onClose();
  };

  const closeAll = () => {
    const { openFiles, closeFile } = useStore.getState();
    // Close in reverse to avoid index issues
    [...openFiles].reverse().forEach((f) => closeFile(f.path));
    onClose();
  };

  const closeSaved = () => {
    const { openFiles, closeFile } = useStore.getState();
    openFiles.forEach((f) => {
      if (!f.dirty) closeFile(f.path);
    });
    onClose();
  };

  const copyPath = () => {
    navigator.clipboard.writeText(filePath);
    showToast("info", "Copied path to clipboard");
    onClose();
  };

  const copyRelativePath = () => {
    const project = useStore.getState().currentProject;
    const relative = project ? filePath.replace(project.path + "/", "") : filePath;
    navigator.clipboard.writeText(relative);
    showToast("info", "Copied relative path to clipboard");
    onClose();
  };

  const revealInExplorer = () => {
    useStore.getState().setSidebarPanel("files");
    onClose();
  };

  const splitRight = () => {
    useStore.getState().openToSide(filePath, "horizontal");
    onClose();
  };

  const splitDown = () => {
    useStore.getState().openToSide(filePath, "vertical");
    onClose();
  };

  const togglePin = () => {
    useStore.getState().pinFile(filePath);
    onClose();
  };

  const file = useStore.getState().openFiles.find((f) => f.path === filePath);
  const isPinned = file?.pinned;

  const items = [
    { label: isPinned ? "Unpin Tab" : "Pin Tab", icon: isPinned ? <PinOff size={13} /> : <Pin size={13} className="-rotate-45" />, action: togglePin, shortcut: undefined },
    { label: "---", icon: null, action: () => {}, shortcut: undefined },
    { label: "Close", icon: <X size={13} />, action: close, shortcut: undefined },
    { label: "Close Others", icon: <XCircle size={13} />, action: closeOthers, shortcut: undefined },
    { label: "Close All", icon: <XCircle size={13} />, action: closeAll, shortcut: undefined },
    { label: "Close Saved", icon: <Files size={13} />, action: closeSaved, shortcut: undefined },
    { label: "---", icon: null, action: () => {}, shortcut: undefined },
    { label: "Split Right", icon: <Columns2 size={13} />, action: splitRight, shortcut: undefined },
    { label: "Split Down", icon: <Rows2 size={13} />, action: splitDown, shortcut: undefined },
    { label: "---", icon: null, action: () => {}, shortcut: undefined },
    { label: "Copy Path", icon: <Copy size={13} />, action: copyPath, shortcut: undefined },
    { label: "Copy Relative Path", icon: <Copy size={13} />, action: copyRelativePath, shortcut: undefined },
    { label: "---", icon: null, action: () => {}, shortcut: undefined },
    { label: "Reveal in Explorer", icon: <FolderOpen size={13} />, action: revealInExplorer, shortcut: undefined },
  ];

  return (
    <div ref={menuRef} style={style}>
      <div className="w-52 rounded-lg border border-obsidian-600 bg-obsidian-800 py-1 shadow-2xl overflow-hidden">
        {items.map((item, i) =>
          item.label === "---" ? (
            <div key={i} className="mx-2 my-1 border-t border-obsidian-600" />
          ) : (
            <button
              key={item.label}
              onClick={item.action}
              className="flex w-full items-center gap-2 px-3 py-1.5 text-xs text-stone-300 hover:bg-obsidian-700 hover:text-stone-100 transition-colors"
            >
              <span className="text-stone-500">{item.icon}</span>
              <span className="flex-1 text-left">{item.label}</span>
            </button>
          ),
        )}
      </div>
    </div>
  );
}
