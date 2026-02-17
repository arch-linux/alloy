/**
 * CodeMirror extension for inline git blame annotations.
 * Shows the last commit author and message for each line in the gutter on hover,
 * and a subtle inline decoration for the active line.
 */

import { EditorView, Decoration, gutter, GutterMarker, ViewPlugin, WidgetType } from "@codemirror/view";
import { StateField, StateEffect, RangeSet } from "@codemirror/state";
import type { DecorationSet, ViewUpdate } from "@codemirror/view";
import type { Extension } from "@codemirror/state";
import { invoke } from "@tauri-apps/api/core";

interface BlameLine {
  hash: string;
  author: string;
  date: string;
  summary: string;
}

// Effect to load blame data
const setBlameData = StateEffect.define<BlameLine[]>();

// State field to hold blame data indexed by line number (0-based)
const blameField = StateField.define<BlameLine[]>({
  create() {
    return [];
  },
  update(value, tr) {
    for (const effect of tr.effects) {
      if (effect.is(setBlameData)) {
        return effect.value;
      }
    }
    return value;
  },
});

// Gutter marker that shows blame info on hover via title attribute
class BlameGutterMarker extends GutterMarker {
  constructor(
    private blame: BlameLine,
  ) {
    super();
  }

  toDOM() {
    const el = document.createElement("div");
    el.className = "cm-blame-gutter-marker";
    // Shorten author to initials or first 2 chars
    const initials = this.blame.author
      .split(/\s+/)
      .map((w) => w[0])
      .join("")
      .slice(0, 2)
      .toUpperCase();
    el.textContent = initials;
    el.title = `${this.blame.author} · ${this.blame.date}\n${this.blame.hash.slice(0, 7)} ${this.blame.summary}`;
    return el;
  }
}

// Blame gutter
const blameGutter = gutter({
  class: "cm-blame-gutter",
  lineMarker(view, line) {
    const blameData = view.state.field(blameField);
    const lineNum = view.state.doc.lineAt(line.from).number - 1;
    if (lineNum >= 0 && lineNum < blameData.length) {
      return new BlameGutterMarker(blameData[lineNum]);
    }
    return null;
  },
  lineMarkerChange(update) {
    return update.transactions.some((tr) =>
      tr.effects.some((e) => e.is(setBlameData)),
    );
  },
});

// Inline blame decoration for the active line
const inlineBlamePlugin = ViewPlugin.fromClass(
  class {
    decorations: DecorationSet;

    constructor(view: EditorView) {
      this.decorations = this.buildDeco(view);
    }

    update(update: ViewUpdate) {
      if (
        update.selectionSet ||
        update.transactions.some((tr) => tr.effects.some((e) => e.is(setBlameData)))
      ) {
        this.decorations = this.buildDeco(update.view);
      }
    }

    buildDeco(view: EditorView): DecorationSet {
      const blameData = view.state.field(blameField);
      if (blameData.length === 0) return Decoration.none;

      const sel = view.state.selection.main;
      const line = view.state.doc.lineAt(sel.head);
      const lineIndex = line.number - 1;

      if (lineIndex < 0 || lineIndex >= blameData.length) return Decoration.none;

      const blame = blameData[lineIndex];
      if (!blame.hash || blame.hash === "0000000000000000000000000000000000000000") {
        return Decoration.none;
      }

      const widget = Decoration.widget({
        widget: new BlameWidget(blame),
        side: 1,
      });

      return Decoration.set([widget.range(line.to)]);
    }
  },
  {
    decorations: (v) => v.decorations,
  },
);

class BlameWidget extends WidgetType {
  constructor(private blame: BlameLine) {
    super();
  }

  eq(other: BlameWidget) {
    return this.blame.hash === other.blame.hash;
  }

  toDOM() {
    const el = document.createElement("span");
    el.className = "cm-blame-inline";
    const ago = this.formatAgo(this.blame.date);
    el.textContent = `  ${this.blame.author}, ${ago} · ${this.blame.summary}`;
    return el;
  }

  ignoreEvent() {
    return true;
  }

  private formatAgo(dateStr: string): string {
    try {
      const date = new Date(dateStr);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24));

      if (diffDays < 1) return "today";
      if (diffDays === 1) return "yesterday";
      if (diffDays < 30) return `${diffDays} days ago`;
      if (diffDays < 365) return `${Math.floor(diffDays / 30)} months ago`;
      return `${Math.floor(diffDays / 365)} years ago`;
    } catch {
      return dateStr;
    }
  }
}

// Theme
const blameTheme = EditorView.baseTheme({
  ".cm-blame-gutter": {
    width: "24px",
    textAlign: "center",
  },
  ".cm-blame-gutter-marker": {
    fontSize: "8px",
    lineHeight: "inherit",
    color: "#3a3a48",
    cursor: "default",
    fontFamily: "Inter, sans-serif",
    letterSpacing: "0.5px",
    "&:hover": {
      color: "#6b7280",
    },
  },
  ".cm-blame-inline": {
    color: "#3a3a48",
    fontSize: "11px",
    fontStyle: "italic",
    fontFamily: "Inter, sans-serif",
    paddingLeft: "16px",
    whiteSpace: "nowrap",
  },
});

/**
 * Create the git blame extension for CodeMirror.
 * Fetches blame data via Tauri backend.
 */
export function gitBlame(filePath: string, projectPath: string | null): Extension {
  if (!projectPath) return [];

  let loaded = false;

  const loader = EditorView.updateListener.of((update) => {
    if (loaded) return;
    loaded = true;

    const relativePath = filePath.startsWith(projectPath)
      ? filePath.slice(projectPath.length + 1)
      : filePath;

    invoke<BlameLine[]>("git_blame_file", {
      projectPath,
      filePath: relativePath,
    })
      .then((data) => {
        update.view.dispatch({
          effects: setBlameData.of(data),
        });
      })
      .catch(() => {
        // No blame data available (new file, not in git, etc.)
      });
  });

  return [blameField, blameGutter, inlineBlamePlugin, blameTheme, loader];
}
