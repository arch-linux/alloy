import { useRef, useEffect, useState, useCallback } from "react";
import { EditorView, keymap } from "@codemirror/view";
import { EditorState, EditorSelection } from "@codemirror/state";
import { basicSetup } from "codemirror";
import { java } from "@codemirror/lang-java";
import { json } from "@codemirror/lang-json";
import { xml } from "@codemirror/lang-xml";
import { yaml } from "@codemirror/lang-yaml";
import { markdown } from "@codemirror/lang-markdown";
import { css } from "@codemirror/lang-css";
import { html } from "@codemirror/lang-html";
import { javascript } from "@codemirror/lang-javascript";
import { indentWithTab } from "@codemirror/commands";
import { alloyTheme, alloyHighlight } from "./alloyTheme";
import Minimap from "./Minimap";
import { useStore } from "../../lib/store";
import type { CursorPosition } from "../../lib/types";

interface CodeEditorProps {
  path: string;
  content: string;
  language: string;
  onChange: (content: string) => void;
  onCursorChange: (pos: CursorPosition) => void;
  onSave: () => void;
}

function getLanguageExtension(lang: string) {
  switch (lang) {
    case "java":
      return java();
    case "json":
      return json();
    case "xml":
      return xml();
    case "yaml":
      return yaml();
    case "markdown":
      return markdown();
    case "css":
      return css();
    case "html":
      return html();
    case "javascript":
      return javascript();
    case "typescript":
      return javascript({ typescript: true, jsx: true });
    case "toml":
      return [];
    case "properties":
      return [];
    default:
      return [];
  }
}

export default function CodeEditor({
  path,
  content,
  language,
  onChange,
  onCursorChange,
  onSave,
}: CodeEditorProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const viewRef = useRef<EditorView>(null);
  const editorSettings = useStore((s) => s.editorSettings);
  const [visibleRange, setVisibleRange] = useState({ start: 1, end: 50 });

  useEffect(() => {
    if (!containerRef.current) return;

    const updateListener = EditorView.updateListener.of((update) => {
      if (update.docChanged) {
        onChange(update.state.doc.toString());
      }
      if (update.selectionSet || update.docChanged) {
        const sel = update.state.selection.main;
        const pos = sel.head;
        const line = update.state.doc.lineAt(pos);
        const selected = Math.abs(sel.to - sel.from);
        onCursorChange({
          line: line.number,
          column: pos - line.from + 1,
          selected: selected > 0 ? selected : undefined,
        });
      }
      // Update visible range for minimap
      if (update.geometryChanged || update.viewportChanged) {
        const view = update.view;
        const startLine = view.state.doc.lineAt(view.viewport.from).number;
        const endLine = view.state.doc.lineAt(view.viewport.to).number;
        setVisibleRange({ start: startLine, end: endLine });
      }
    });

    const saveKeymap = keymap.of([
      {
        key: "Mod-s",
        run: () => {
          onSave();
          return true;
        },
      },
    ]);

    const fontTheme = EditorView.theme({
      "&": { fontSize: editorSettings.fontSize + "px" },
      ".cm-content": { fontFamily: '"JetBrains Mono", monospace' },
    });

    const extensions = [
      basicSetup,
      alloyTheme,
      fontTheme,
      alloyHighlight,
      getLanguageExtension(language),
      keymap.of([indentWithTab]),
      saveKeymap,
      updateListener,
      EditorState.tabSize.of(editorSettings.tabSize),
    ];

    if (editorSettings.wordWrap) {
      extensions.push(EditorView.lineWrapping);
    }

    const state = EditorState.create({
      doc: content,
      extensions,
    });

    const view = new EditorView({
      state,
      parent: containerRef.current,
    });

    viewRef.current = view;

    // Initial visible range
    const startLine = view.state.doc.lineAt(view.viewport.from).number;
    const endLine = view.state.doc.lineAt(view.viewport.to).number;
    setVisibleRange({ start: startLine, end: endLine });

    return () => {
      view.destroy();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [path, editorSettings.fontSize, editorSettings.tabSize, editorSettings.wordWrap]);

  // Handle go-to-line requests
  const pendingGoToLine = useStore((s) => s.pendingGoToLine);
  const clearGoToLine = useStore((s) => s.clearGoToLine);

  useEffect(() => {
    if (pendingGoToLine === null || !viewRef.current) return;
    const view = viewRef.current;
    const doc = view.state.doc;
    const lineNum = Math.min(pendingGoToLine, doc.lines);
    const line = doc.line(lineNum);
    view.dispatch({
      selection: EditorSelection.cursor(line.from),
      scrollIntoView: true,
    });
    view.focus();
    clearGoToLine();
  }, [pendingGoToLine, clearGoToLine]);

  const scrollToLine = useCallback((line: number) => {
    const view = viewRef.current;
    if (!view) return;
    const doc = view.state.doc;
    const lineNum = Math.min(line, doc.lines);
    const lineObj = doc.line(lineNum);
    view.dispatch({
      effects: EditorView.scrollIntoView(lineObj.from, { y: "start" }),
    });
  }, []);

  const totalLines = content.split("\n").length;

  return (
    <div className="h-full w-full overflow-hidden flex">
      <div ref={containerRef} className="flex-1 min-w-0 overflow-hidden" />
      {editorSettings.minimap && (
        <Minimap
          content={content}
          visibleStart={visibleRange.start}
          visibleEnd={visibleRange.end}
          totalLines={totalLines}
          onScrollTo={scrollToLine}
        />
      )}
    </div>
  );
}
