import { EditorView, hoverTooltip, keymap, showTooltip } from "@codemirror/view";
import type { Tooltip } from "@codemirror/view";
import { StateField, StateEffect } from "@codemirror/state";
import type { Extension } from "@codemirror/state";
import type { CompletionContext, CompletionResult, CompletionSource } from "@codemirror/autocomplete";
import { setDiagnostics } from "@codemirror/lint";
import type { Diagnostic } from "@codemirror/lint";
import {
  lspCompletion,
  lspHover,
  lspDefinition,
  lspReferences,
  lspCodeActions,
  lspSignatureHelp,
  lspDidChange,
  lspDidClose,
  lspPrepareRename,
  onLspDiagnostics,
} from "../../lib/lsp";
import type { LspCompletionItem, LspDiagnostic, LspCodeAction } from "../../lib/lsp";

// Map LSP completion kinds to CodeMirror types
function mapCompletionKind(kind: string): string {
  switch (kind) {
    case "method":
    case "function":
      return "function";
    case "constructor":
      return "function";
    case "field":
    case "property":
      return "property";
    case "variable":
      return "variable";
    case "class":
    case "struct":
      return "class";
    case "interface":
      return "interface";
    case "module":
      return "namespace";
    case "enum":
    case "enum_member":
      return "enum";
    case "keyword":
      return "keyword";
    case "snippet":
      return "text";
    case "constant":
      return "constant";
    case "type_parameter":
      return "type";
    default:
      return "text";
  }
}

// Track document version per file for LSP didChange notifications
const versionMap = new Map<string, number>();

function getVersion(path: string): number {
  const v = (versionMap.get(path) || 0) + 1;
  versionMap.set(path, v);
  return v;
}

/**
 * Creates a completion source for the LSP backend.
 */
export function createLspCompletionSource(filePath: string): CompletionSource {
  return async (context: CompletionContext): Promise<CompletionResult | null> => {
    if (!context.explicit && !context.matchBefore(/\w+/)) {
      return null;
    }

    const pos = context.pos;
    const line = context.state.doc.lineAt(pos);
    const lspLine = line.number - 1;
    const lspChar = pos - line.from;

    try {
      const items: LspCompletionItem[] = await lspCompletion(filePath, lspLine, lspChar);
      if (items.length === 0) return null;

      const word = context.matchBefore(/[\w.]+/);
      const from = word ? word.from : pos;

      return {
        from,
        options: items.map((item) => ({
          label: item.label,
          type: mapCompletionKind(item.kind),
          detail: item.detail || undefined,
          apply: item.insert_text || item.label,
          boost: item.sort_text ? 100 - parseInt(item.sort_text, 10) : undefined,
        })),
      };
    } catch {
      return null;
    }
  };
}

/**
 * Creates a CodeMirror extension that integrates with the LSP backend.
 * Provides: hover tooltips, diagnostics, go-to-definition, signature help.
 * NOTE: Autocompletion is NOT included — use createLspCompletionSource() separately.
 */
export function lspExtension(filePath: string): Extension {
  // Debounce timer for didChange notifications
  let changeTimer: ReturnType<typeof setTimeout> | null = null;
  let lastContent = "";

  // Hover tooltip provider
  const hoverProvider = hoverTooltip(
    async (view: EditorView, pos: number): Promise<Tooltip | null> => {
      const line = view.state.doc.lineAt(pos);
      const lspLine = line.number - 1;
      const lspChar = pos - line.from;

      try {
        const result = await lspHover(filePath, lspLine, lspChar);
        if (!result || !result.contents) return null;

        return {
          pos,
          above: true,
          create() {
            const dom = document.createElement("div");
            dom.className = "cm-lsp-hover";
            // Render markdown-ish content as HTML
            dom.innerHTML = formatHoverContent(result.contents);
            return { dom };
          },
        };
      } catch {
        return null;
      }
    },
    { hideOnChange: true }
  );

  // Go-to-definition (F12) and rename symbol (F2)
  const definitionKeymap = keymap.of([
    {
      key: "F12",
      run: (view) => {
        goToDefinition(view, filePath);
        return true;
      },
    },
    {
      key: "F2",
      run: (view) => {
        triggerRename(view, filePath);
        return true;
      },
    },
    {
      key: "Shift-F12",
      run: (view) => {
        findReferences(view, filePath);
        return true;
      },
    },
    {
      key: "Mod-.",
      run: (view) => {
        showCodeActions(view, filePath);
        return true;
      },
    },
  ]);

  // Ctrl+Click (Cmd+Click on Mac) for go-to-definition
  const ctrlClickHandler = EditorView.domEventHandlers({
    mousedown(event: MouseEvent, view: EditorView) {
      if (event.metaKey || event.ctrlKey) {
        const pos = view.posAtCoords({ x: event.clientX, y: event.clientY });
        if (pos !== null) {
          event.preventDefault();
          // Set cursor to click position then go-to-definition
          view.dispatch({ selection: { anchor: pos } });
          goToDefinition(view, filePath);
          return true;
        }
      }
      return false;
    },
  });

  // Underline text on Ctrl/Cmd hover (visual affordance)
  const ctrlHoverStyle = EditorView.theme({
    "&.cm-ctrl-hover .cm-content": {
      cursor: "pointer",
    },
  });

  // Signature help tooltip
  const setSignatureTooltip = StateEffect.define<Tooltip | null>();
  const signatureField = StateField.define<Tooltip | null>({
    create: () => null,
    update(value, tr) {
      for (const e of tr.effects) {
        if (e.is(setSignatureTooltip)) return e.value;
      }
      return value;
    },
    provide: (f) => showTooltip.from(f),
  });

  let sigTimer: ReturnType<typeof setTimeout> | null = null;

  const signatureListener = EditorView.updateListener.of((update) => {
    if (!update.docChanged && !update.selectionSet) return;

    if (sigTimer) clearTimeout(sigTimer);
    sigTimer = setTimeout(async () => {
      const pos = update.state.selection.main.head;
      // Check if we're inside parentheses
      const textBefore = update.state.doc.sliceString(
        Math.max(0, pos - 100),
        pos
      );
      const openCount = (textBefore.match(/\(/g) || []).length;
      const closeCount = (textBefore.match(/\)/g) || []).length;

      if (openCount <= closeCount) {
        // Not inside parentheses — clear tooltip
        update.view.dispatch({
          effects: setSignatureTooltip.of(null),
        });
        return;
      }

      const line = update.state.doc.lineAt(pos);
      const lspLine = line.number - 1;
      const lspChar = pos - line.from;

      try {
        const result = await lspSignatureHelp(filePath, lspLine, lspChar);
        if (!result || result.signatures.length === 0) {
          update.view.dispatch({
            effects: setSignatureTooltip.of(null),
          });
          return;
        }

        const sig = result.signatures[result.active_signature] || result.signatures[0];
        const tooltip: Tooltip = {
          pos,
          above: true,
          create() {
            const dom = document.createElement("div");
            dom.className = "cm-sig-help";

            // Build signature with highlighted parameter
            let html = '<span class="sig-label">';
            if (sig.parameters.length > 0) {
              const parts = sig.label.split(/[(),]/);
              const fnName = sig.label.substring(0, sig.label.indexOf("(") + 1);
              html += fnName;

              sig.parameters.forEach((param, i) => {
                if (i > 0) html += ", ";
                if (i === result.active_parameter) {
                  html += `<strong class="sig-active">${param}</strong>`;
                } else {
                  html += param;
                }
              });

              html += ")";
              if (parts.length > 0) {
                const afterParen = sig.label.substring(sig.label.lastIndexOf(")") + 1);
                if (afterParen) html += afterParen;
              }
            } else {
              html += sig.label;
            }

            html += "</span>";
            dom.innerHTML = html;
            return { dom };
          },
        };

        update.view.dispatch({
          effects: setSignatureTooltip.of(tooltip),
        });
      } catch {
        update.view.dispatch({
          effects: setSignatureTooltip.of(null),
        });
      }
    }, 200);
  });

  const signatureTheme = EditorView.theme({
    ".cm-sig-help": {
      padding: "4px 8px",
      maxWidth: "500px",
      fontSize: "12px",
      fontFamily: '"JetBrains Mono", monospace',
      backgroundColor: "#14141c",
      border: "1px solid #2a2a36",
      borderRadius: "4px",
      color: "#b8bfc9",
    },
    ".cm-sig-help .sig-active": {
      color: "#ff6b00",
      fontWeight: "bold",
    },
    ".cm-sig-help .sig-label": {
      color: "#d1d5db",
    },
  });

  // Update listener for didChange/didSave notifications
  const updateListener = EditorView.updateListener.of((update) => {
    if (update.docChanged) {
      const content = update.state.doc.toString();
      // Debounce didChange to avoid flooding the LSP
      if (changeTimer) clearTimeout(changeTimer);
      changeTimer = setTimeout(() => {
        if (content !== lastContent) {
          lastContent = content;
          lspDidChange(filePath, getVersion(filePath), content).catch(
            () => {}
          );
        }
      }, 300);
    }
  });

  // Theme for hover tooltips
  const hoverTheme = EditorView.theme({
    ".cm-lsp-hover": {
      padding: "8px 12px",
      maxWidth: "500px",
      maxHeight: "300px",
      overflow: "auto",
      fontSize: "12px",
      lineHeight: "1.5",
      fontFamily: '"JetBrains Mono", monospace',
      backgroundColor: "#14141c",
      border: "1px solid #2a2a36",
      borderRadius: "4px",
      color: "#f0f0f4",
    },
    ".cm-lsp-hover code": {
      backgroundColor: "#1e1e28",
      padding: "1px 4px",
      borderRadius: "2px",
      fontSize: "11px",
    },
    ".cm-lsp-hover .lsp-type": {
      color: "#ff6b00",
    },
    ".cm-lsp-hover .lsp-keyword": {
      color: "#ff8a33",
    },
  });

  return [
    hoverProvider,
    definitionKeymap,
    ctrlClickHandler,
    ctrlHoverStyle,
    updateListener,
    signatureField,
    signatureListener,
    signatureTheme,
    hoverTheme,
  ];
}

/**
 * Set up LSP diagnostics listener that updates CodeMirror lint markers.
 * Returns a cleanup function.
 */
export function setupLspDiagnostics(
  filePath: string,
  getView: () => EditorView | null
): () => void {
  let unlisten: (() => void) | null = null;

  onLspDiagnostics((diagnostics: LspDiagnostic[]) => {
    const view = getView();
    if (!view) return;

    // Filter diagnostics for this file
    const fileDiags = diagnostics.filter((d) => d.path === filePath);

    const cmDiags: Diagnostic[] = fileDiags
      .map((d) => {
        const doc = view.state.doc;
        // Convert LSP 0-based positions to CodeMirror offsets
        const startLine = Math.min(d.line + 1, doc.lines);
        const endLine = Math.min(d.end_line + 1, doc.lines);
        const line = doc.line(startLine);
        const endLineObj = doc.line(endLine);

        const from = Math.min(line.from + d.character, line.to);
        const to = Math.min(endLineObj.from + d.end_character, endLineObj.to);

        return {
          from,
          to: Math.max(to, from + 1), // Ensure at least 1 char span
          severity: mapSeverity(d.severity),
          message: d.message,
          source: d.source || "jdtls",
        };
      })
      .filter((d) => d.from >= 0 && d.to <= view.state.doc.length);

    view.dispatch(setDiagnostics(view.state, cmDiags));
  }).then((fn) => {
    unlisten = fn;
  });

  return () => {
    if (unlisten) unlisten();
    lspDidClose(filePath).catch(() => {});
  };
}

function mapSeverity(severity: string): "error" | "warning" | "info" | "hint" {
  switch (severity) {
    case "error":
      return "error";
    case "warning":
      return "warning";
    case "info":
      return "info";
    case "hint":
      return "info";
    default:
      return "info";
  }
}

async function goToDefinition(view: EditorView, filePath: string) {
  const pos = view.state.selection.main.head;
  const line = view.state.doc.lineAt(pos);
  const lspLine = line.number - 1;
  const lspChar = pos - line.from;

  try {
    const locations = await lspDefinition(filePath, lspLine, lspChar);
    if (locations.length > 0) {
      const loc = locations[0];
      // If same file, jump to position
      if (loc.uri === filePath) {
        const targetLine = Math.min(loc.line + 1, view.state.doc.lines);
        const lineObj = view.state.doc.line(targetLine);
        const targetPos = Math.min(lineObj.from + loc.character, lineObj.to);
        view.dispatch({
          selection: { anchor: targetPos },
          scrollIntoView: true,
        });
        view.focus();
      } else {
        // Different file — dispatch custom event for the IDE to handle
        window.dispatchEvent(
          new CustomEvent("lsp:goto", {
            detail: {
              path: loc.uri,
              line: loc.line + 1,
              column: loc.character + 1,
            },
          })
        );
      }
    }
  } catch {
    // Silently ignore errors
  }
}

async function triggerRename(view: EditorView, filePath: string) {
  const pos = view.state.selection.main.head;
  const line = view.state.doc.lineAt(pos);
  const lspLine = line.number - 1;
  const lspChar = pos - line.from;

  try {
    const placeholder = await lspPrepareRename(filePath, lspLine, lspChar);
    if (placeholder === null) return;

    // Dispatch custom event for the IDE to show a rename dialog
    window.dispatchEvent(
      new CustomEvent("lsp:rename", {
        detail: {
          path: filePath,
          line: lspLine,
          character: lspChar,
          currentName: placeholder,
        },
      })
    );
  } catch {
    // If prepareRename fails, try getting word under cursor as fallback
    const word = view.state.wordAt(pos);
    if (!word) return;
    const currentName = view.state.doc.sliceString(word.from, word.to);

    window.dispatchEvent(
      new CustomEvent("lsp:rename", {
        detail: {
          path: filePath,
          line: lspLine,
          character: lspChar,
          currentName,
        },
      })
    );
  }
}

async function showCodeActions(view: EditorView, filePath: string) {
  const sel = view.state.selection.main;
  const startLine = view.state.doc.lineAt(sel.from);
  const endLine = view.state.doc.lineAt(sel.to);

  const lspStartLine = startLine.number - 1;
  const lspStartChar = sel.from - startLine.from;
  const lspEndLine = endLine.number - 1;
  const lspEndChar = sel.to - endLine.from;

  try {
    const actions = await lspCodeActions(
      filePath,
      lspStartLine,
      lspStartChar,
      lspEndLine,
      lspEndChar
    );

    if (actions.length === 0) return;

    // Dispatch custom event for the IDE to show a code actions menu
    const coords = view.coordsAtPos(sel.head);
    window.dispatchEvent(
      new CustomEvent("lsp:codeActions", {
        detail: {
          actions,
          x: coords?.left ?? 0,
          y: (coords?.bottom ?? 0) + 4,
        },
      })
    );
  } catch {
    // Silently ignore
  }
}

async function findReferences(view: EditorView, filePath: string) {
  const pos = view.state.selection.main.head;
  const line = view.state.doc.lineAt(pos);
  const lspLine = line.number - 1;
  const lspChar = pos - line.from;

  // Get the word under cursor for display
  const word = view.state.wordAt(pos);
  const symbolName = word
    ? view.state.doc.sliceString(word.from, word.to)
    : "symbol";

  try {
    const locations = await lspReferences(filePath, lspLine, lspChar);
    window.dispatchEvent(
      new CustomEvent("lsp:references", {
        detail: {
          symbol: symbolName,
          locations: locations.map((loc) => ({
            path: loc.uri,
            line: loc.line + 1,
            character: loc.character + 1,
          })),
        },
      })
    );
  } catch {
    // Silently ignore
  }
}

function formatHoverContent(content: string): string {
  // Simple markdown-to-HTML: code blocks, inline code, bold
  return content
    .replace(/```(\w+)?\n([\s\S]*?)```/g, '<pre><code>$2</code></pre>')
    .replace(/`([^`]+)`/g, "<code>$1</code>")
    .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
    .replace(/\n/g, "<br>");
}
