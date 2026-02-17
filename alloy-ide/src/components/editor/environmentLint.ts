/**
 * CodeMirror extension for Alloy environment linting.
 * Shows inline diagnostics when a mod imports classes from the wrong environment.
 */

import { EditorView, Decoration, gutter, GutterMarker } from "@codemirror/view";
import { StateField, StateEffect, RangeSet } from "@codemirror/state";
import type { DecorationSet } from "@codemirror/view";
import type { Extension } from "@codemirror/state";
import { analyzeEnvironment } from "../../lib/environment";
import type { ModEnvironment } from "../../lib/types";
import type { EnvironmentDiagnostic } from "../../lib/environment";

// Effect to set diagnostics
const setDiagnostics = StateEffect.define<EnvironmentDiagnostic[]>();

// State field to hold current diagnostics
const diagnosticsField = StateField.define<EnvironmentDiagnostic[]>({
  create() {
    return [];
  },
  update(value, tr) {
    for (const effect of tr.effects) {
      if (effect.is(setDiagnostics)) {
        return effect.value;
      }
    }
    return value;
  },
});

// Decoration for error lines
const errorLineDeco = Decoration.line({ class: "cm-env-error-line" });
const warningLineDeco = Decoration.line({ class: "cm-env-warning-line" });

// Decoration state field
const decorationsField = StateField.define<DecorationSet>({
  create() {
    return Decoration.none;
  },
  update(value, tr) {
    for (const effect of tr.effects) {
      if (effect.is(setDiagnostics)) {
        const diagnostics = effect.value;
        const decos: { from: number; deco: Decoration }[] = [];

        for (const diag of diagnostics) {
          if (diag.line <= tr.state.doc.lines) {
            const line = tr.state.doc.line(diag.line);
            decos.push({
              from: line.from,
              deco: diag.severity === "error" ? errorLineDeco : warningLineDeco,
            });
          }
        }

        // Sort by position
        decos.sort((a, b) => a.from - b.from);
        return RangeSet.of(decos.map((d) => d.deco.range(d.from)));
      }
    }
    // Re-map decorations on doc changes
    if (tr.docChanged) {
      return value.map(tr.changes);
    }
    return value;
  },
  provide: (f) => EditorView.decorations.from(f),
});

// Gutter marker for env diagnostics
class EnvGutterMarker extends GutterMarker {
  constructor(readonly severity: "error" | "warning", readonly message: string) {
    super();
  }

  toDOM() {
    const el = document.createElement("span");
    el.className = this.severity === "error" ? "cm-env-gutter-error" : "cm-env-gutter-warning";
    el.title = this.message;
    el.textContent = this.severity === "error" ? "●" : "▲";
    return el;
  }
}

// Gutter for environment diagnostics
const envGutter = gutter({
  class: "cm-env-gutter",
  markers(view) {
    const diagnostics = view.state.field(diagnosticsField);
    const markers: { from: number; marker: GutterMarker }[] = [];

    for (const diag of diagnostics) {
      if (diag.line <= view.state.doc.lines) {
        const line = view.state.doc.line(diag.line);
        markers.push({
          from: line.from,
          marker: new EnvGutterMarker(diag.severity, diag.message),
        });
      }
    }

    markers.sort((a, b) => a.from - b.from);
    return RangeSet.of(markers.map((m) => m.marker.range(m.from)));
  },
});

// Theme for environment lint decorations
const envLintTheme = EditorView.baseTheme({
  ".cm-env-error-line": {
    backgroundColor: "rgba(239, 68, 68, 0.06)",
    borderLeft: "2px solid rgba(239, 68, 68, 0.5)",
  },
  ".cm-env-warning-line": {
    backgroundColor: "rgba(234, 179, 8, 0.04)",
    borderLeft: "2px solid rgba(234, 179, 8, 0.4)",
  },
  ".cm-env-gutter": {
    width: "14px",
  },
  ".cm-env-gutter-error": {
    color: "#ef4444",
    fontSize: "10px",
    cursor: "pointer",
  },
  ".cm-env-gutter-warning": {
    color: "#eab308",
    fontSize: "10px",
    cursor: "pointer",
  },
});

/**
 * Create the environment lint extension for CodeMirror.
 * Re-analyzes on document changes with a debounce.
 */
export function environmentLint(environment: ModEnvironment | null): Extension {
  if (!environment) return [];

  let debounceTimer: ReturnType<typeof setTimeout> | null = null;

  const analysisPlugin = EditorView.updateListener.of((update) => {
    if (!update.docChanged && !update.startState.field(diagnosticsField).length) {
      // Only run on initial load or doc changes
      if (update.startState.doc.length > 0) return;
    }

    if (debounceTimer) clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      const content = update.state.doc.toString();
      const diagnostics = analyzeEnvironment(content, environment);
      update.view.dispatch({
        effects: setDiagnostics.of(diagnostics),
      });
    }, 500);
  });

  // Initial analysis
  const initialAnalysis = EditorView.updateListener.of((update) => {
    if (update.startState.doc.length === 0 && update.state.doc.length > 0) {
      const content = update.state.doc.toString();
      const diagnostics = analyzeEnvironment(content, environment);
      update.view.dispatch({
        effects: setDiagnostics.of(diagnostics),
      });
    }
  });

  return [
    diagnosticsField,
    decorationsField,
    envGutter,
    envLintTheme,
    analysisPlugin,
    initialAnalysis,
  ];
}
