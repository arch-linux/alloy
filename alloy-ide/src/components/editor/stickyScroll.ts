/**
 * Sticky scroll for CodeMirror 6.
 * Shows the enclosing class/method at the top of the editor when scrolled past.
 */

import { EditorView, ViewPlugin, type ViewUpdate } from "@codemirror/view";
import type { Extension } from "@codemirror/state";

interface ScopeInfo {
  label: string;
  line: number;
}

/** Extract class/method scopes from Java-like source. */
function extractScopes(text: string): { line: number; endLine: number; label: string; depth: number }[] {
  const lines = text.split("\n");
  const scopes: { line: number; endLine: number; label: string; depth: number }[] = [];
  const stack: { line: number; label: string; braceCount: number }[] = [];
  let braceDepth = 0;

  // Patterns for class/interface/enum and method declarations
  const classPattern = /^\s*(?:(?:public|private|protected|static|abstract|final)\s+)*(?:class|interface|enum)\s+(\w+)/;
  const methodPattern = /^\s*(?:(?:public|private|protected|static|abstract|final|synchronized|native)\s+)*(?:[\w<>\[\],\s]+)\s+(\w+)\s*\(/;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    // Skip comments and empty lines for scope detection
    if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*") || trimmed === "") {
      // Still count braces in block comments closing
      for (const ch of line) {
        if (ch === "{") braceDepth++;
        if (ch === "}") {
          braceDepth--;
          if (stack.length > 0 && braceDepth <= stack[stack.length - 1].braceCount) {
            const scope = stack.pop()!;
            scopes.push({
              line: scope.line,
              endLine: i + 1,
              label: scope.label,
              depth: stack.length,
            });
          }
        }
      }
      continue;
    }

    // Check for class declaration
    const classMatch = trimmed.match(classPattern);
    if (classMatch) {
      stack.push({ line: i + 1, label: `class ${classMatch[1]}`, braceCount: braceDepth });
    }
    // Check for method declaration (only inside a class)
    else if (stack.length > 0) {
      const methodMatch = trimmed.match(methodPattern);
      if (methodMatch && !trimmed.startsWith("new ") && !trimmed.startsWith("return ")) {
        // Avoid matching control flow (if, for, while, etc.)
        const name = methodMatch[1];
        if (!["if", "for", "while", "switch", "catch", "else"].includes(name)) {
          stack.push({ line: i + 1, label: `${name}()`, braceCount: braceDepth });
        }
      }
    }

    // Count braces on this line
    let inStr = false;
    let strChar = "";
    for (let j = 0; j < line.length; j++) {
      const ch = line[j];
      if (inStr) {
        if (ch === "\\" && j + 1 < line.length) { j++; continue; }
        if (ch === strChar) inStr = false;
        continue;
      }
      if (ch === '"' || ch === "'") { inStr = true; strChar = ch; continue; }
      if (ch === "/" && line[j + 1] === "/") break;
      if (ch === "{") braceDepth++;
      if (ch === "}") {
        braceDepth--;
        if (stack.length > 0 && braceDepth <= stack[stack.length - 1].braceCount) {
          const scope = stack.pop()!;
          scopes.push({
            line: scope.line,
            endLine: i + 1,
            label: scope.label,
            depth: stack.length,
          });
        }
      }
    }
  }

  // Close any remaining scopes
  while (stack.length > 0) {
    const scope = stack.pop()!;
    scopes.push({
      line: scope.line,
      endLine: lines.length,
      label: scope.label,
      depth: stack.length,
    });
  }

  return scopes;
}

/** Find active scopes for a given visible line number */
function getActiveScopesAtLine(
  scopes: { line: number; endLine: number; label: string; depth: number }[],
  lineNum: number,
): ScopeInfo[] {
  const active: ScopeInfo[] = [];
  for (const scope of scopes) {
    if (scope.line < lineNum && scope.endLine >= lineNum) {
      active.push({ label: scope.label, line: scope.line });
    }
  }
  // Sort by line (outermost first)
  active.sort((a, b) => a.line - b.line);
  return active;
}

const stickyPlugin = ViewPlugin.fromClass(
  class {
    dom: HTMLElement;
    scopes: { line: number; endLine: number; label: string; depth: number }[] = [];
    lastDoc = "";

    constructor(view: EditorView) {
      this.dom = document.createElement("div");
      this.dom.className = "cm-sticky-scroll";
      this.dom.style.cssText =
        "position: absolute; top: 0; left: 0; right: 0; z-index: 5; pointer-events: none;";
      view.dom.style.position = "relative";
      view.dom.appendChild(this.dom);
      this.updateScopes(view);
      this.updateDisplay(view);
    }

    updateScopes(view: EditorView) {
      const doc = view.state.doc.toString();
      if (doc === this.lastDoc) return;
      this.lastDoc = doc;
      this.scopes = extractScopes(doc);
    }

    updateDisplay(view: EditorView) {
      const firstVisibleLine = view.state.doc.lineAt(view.viewport.from).number;
      const active = getActiveScopesAtLine(this.scopes, firstVisibleLine);

      if (active.length === 0) {
        this.dom.innerHTML = "";
        return;
      }

      // Show up to 2 scope levels
      const shown = active.slice(-2);

      this.dom.innerHTML = shown
        .map(
          (s, i) =>
            `<div style="
              padding: ${i === 0 ? "4px" : "2px"} 16px ${i === shown.length - 1 ? "4px" : "2px"} ${16 + i * 16}px;
              background: #0c0c12;
              border-bottom: ${i === shown.length - 1 ? "1px solid #1e1e28" : "none"};
              font-size: 12px;
              font-family: 'JetBrains Mono', monospace;
              color: ${i === shown.length - 1 ? "#d1d5db" : "#6b7280"};
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            ">${escapeHtml(s.label)}</div>`,
        )
        .join("");
    }

    update(update: ViewUpdate) {
      if (update.docChanged) {
        this.updateScopes(update.view);
      }
      if (update.docChanged || update.viewportChanged || update.geometryChanged) {
        this.updateDisplay(update.view);
      }
    }

    destroy() {
      this.dom.remove();
    }
  },
);

function escapeHtml(s: string): string {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/**
 * Extension for sticky scroll scope display.
 */
export function stickyScroll(): Extension {
  return [stickyPlugin];
}
