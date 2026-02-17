import {
  Decoration,
  EditorView,
  ViewPlugin,
} from "@codemirror/view";
import type { DecorationSet, ViewUpdate } from "@codemirror/view";
import { RangeSetBuilder } from "@codemirror/state";

const guideMark = Decoration.line({
  class: "cm-indent-guide",
});

/**
 * Indent guide plugin for CodeMirror 6.
 * Adds subtle vertical lines at each indentation level.
 */
function buildGuides(view: EditorView): DecorationSet {
  const builder = new RangeSetBuilder<Decoration>();
  const { from, to } = view.viewport;
  const doc = view.state.doc;
  const tabSize = view.state.tabSize;

  // Process visible lines
  for (let pos = from; pos <= to; ) {
    const line = doc.lineAt(pos);
    const text = line.text;

    // Calculate indentation level
    let indent = 0;
    for (let i = 0; i < text.length; i++) {
      if (text[i] === " ") indent++;
      else if (text[i] === "\t") indent += tabSize;
      else break;
    }

    // Add guide markers for indented lines
    if (indent >= tabSize) {
      builder.add(line.from, line.from, guideMark);
    }

    pos = line.to + 1;
  }

  return builder.finish();
}

const indentGuidePlugin = ViewPlugin.fromClass(
  class {
    decorations: DecorationSet;
    constructor(view: EditorView) {
      this.decorations = buildGuides(view);
    }
    update(update: ViewUpdate) {
      if (update.docChanged || update.viewportChanged) {
        this.decorations = buildGuides(update.view);
      }
    }
  },
  {
    decorations: (v) => v.decorations,
  }
);

const indentGuideTheme = EditorView.baseTheme({
  ".cm-indent-guide": {
    backgroundImage:
      "linear-gradient(to right, transparent 0px, transparent calc(var(--indent-size) - 1px), rgba(58, 58, 72, 0.5) calc(var(--indent-size) - 1px), rgba(58, 58, 72, 0.5) var(--indent-size))",
    backgroundSize: "var(--indent-size) 100%",
    backgroundRepeat: "repeat-x",
    backgroundPosition: "left",
  },
});

/**
 * Creates indent guide extensions for CodeMirror.
 * Uses CSS custom property --indent-size to set the guide spacing.
 */
export function indentGuides(tabSize: number = 4) {
  return [
    indentGuidePlugin,
    indentGuideTheme,
    EditorView.theme({
      ".cm-line": {
        "--indent-size": `${tabSize}ch`,
      } as Record<string, string>,
    }),
  ];
}
