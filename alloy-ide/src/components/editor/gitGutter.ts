/**
 * CodeMirror extension for git change gutter markers.
 * Shows colored indicators for added, modified, and deleted lines
 * compared to the git HEAD version of the file.
 */

import { EditorView, gutter, GutterMarker } from "@codemirror/view";
import { StateField, StateEffect, RangeSet } from "@codemirror/state";
import type { Extension } from "@codemirror/state";
import { invoke } from "@tauri-apps/api/core";

type ChangeType = "added" | "modified" | "deleted";

interface LineChange {
  line: number;
  type: ChangeType;
}

// Effect to set git changes
const setGitChanges = StateEffect.define<LineChange[]>();

// State field to hold git changes
const gitChangesField = StateField.define<LineChange[]>({
  create() {
    return [];
  },
  update(value, tr) {
    for (const effect of tr.effects) {
      if (effect.is(setGitChanges)) {
        return effect.value;
      }
    }
    return value;
  },
});

// Gutter markers
class AddedMarker extends GutterMarker {
  toDOM() {
    const el = document.createElement("div");
    el.className = "cm-git-gutter-added";
    return el;
  }
}

class ModifiedMarker extends GutterMarker {
  toDOM() {
    const el = document.createElement("div");
    el.className = "cm-git-gutter-modified";
    return el;
  }
}

class DeletedMarker extends GutterMarker {
  toDOM() {
    const el = document.createElement("div");
    el.className = "cm-git-gutter-deleted";
    return el;
  }
}

const addedMarker = new AddedMarker();
const modifiedMarker = new ModifiedMarker();
const deletedMarker = new DeletedMarker();

// Git gutter
const gitGutterExtension = gutter({
  class: "cm-git-gutter",
  markers(view) {
    const changes = view.state.field(gitChangesField);
    const markers: { from: number; marker: GutterMarker }[] = [];

    for (const change of changes) {
      if (change.line > 0 && change.line <= view.state.doc.lines) {
        const line = view.state.doc.line(change.line);
        const marker =
          change.type === "added"
            ? addedMarker
            : change.type === "modified"
              ? modifiedMarker
              : deletedMarker;
        markers.push({ from: line.from, marker });
      }
    }

    markers.sort((a, b) => a.from - b.from);
    return RangeSet.of(markers.map((m) => m.marker.range(m.from)));
  },
});

// Theme
const gitGutterTheme = EditorView.baseTheme({
  ".cm-git-gutter": {
    width: "3px",
    marginRight: "2px",
  },
  ".cm-git-gutter-added": {
    width: "3px",
    height: "100%",
    backgroundColor: "#22c55e",
    borderRadius: "1px",
  },
  ".cm-git-gutter-modified": {
    width: "3px",
    height: "100%",
    backgroundColor: "#3b82f6",
    borderRadius: "1px",
  },
  ".cm-git-gutter-deleted": {
    width: "3px",
    height: "2px",
    backgroundColor: "#ef4444",
    borderRadius: "1px",
    marginTop: "auto",
    marginBottom: "auto",
  },
});

/**
 * Simple line-by-line diff to find changes.
 */
function computeLineChanges(original: string, current: string): LineChange[] {
  const origLines = original.split("\n");
  const currLines = current.split("\n");
  const changes: LineChange[] = [];

  // Simple LCS-based diff
  const maxLen = Math.max(origLines.length, currLines.length);
  let oi = 0;
  let ci = 0;

  while (oi < origLines.length || ci < currLines.length) {
    if (oi >= origLines.length) {
      // All remaining current lines are added
      changes.push({ line: ci + 1, type: "added" });
      ci++;
    } else if (ci >= currLines.length) {
      // Remaining original lines were deleted
      // Mark as deleted at the end of current file
      changes.push({ line: Math.min(ci + 1, currLines.length), type: "deleted" });
      oi++;
    } else if (origLines[oi] === currLines[ci]) {
      // Lines match
      oi++;
      ci++;
    } else {
      // Check if it's a modification or an insert/delete
      // Look ahead to find a match
      const lookAhead = 3;
      let foundOrig = -1;
      let foundCurr = -1;

      for (let d = 1; d <= lookAhead; d++) {
        if (oi + d < origLines.length && origLines[oi + d] === currLines[ci]) {
          foundOrig = d;
          break;
        }
        if (ci + d < currLines.length && origLines[oi] === currLines[ci + d]) {
          foundCurr = d;
          break;
        }
      }

      if (foundOrig > 0) {
        // Lines were deleted from original
        for (let d = 0; d < foundOrig; d++) {
          changes.push({ line: ci + 1, type: "deleted" });
          oi++;
        }
      } else if (foundCurr > 0) {
        // Lines were added in current
        for (let d = 0; d < foundCurr; d++) {
          changes.push({ line: ci + 1, type: "added" });
          ci++;
        }
      } else {
        // Modified line
        changes.push({ line: ci + 1, type: "modified" });
        oi++;
        ci++;
      }
    }
  }

  return changes;
}

/**
 * Create the git gutter extension for CodeMirror.
 * Fetches the HEAD version of the file and computes changes.
 */
export function gitGutter(filePath: string, projectPath: string | null): Extension {
  if (!projectPath) return [];

  let debounceTimer: ReturnType<typeof setTimeout> | null = null;
  let originalContent: string | null = null;
  let fetching = false;

  const fetchOriginal = async () => {
    if (fetching) return;
    fetching = true;
    try {
      // Get file content from git HEAD
      const relativePath = filePath.startsWith(projectPath)
        ? filePath.slice(projectPath.length + 1)
        : filePath;
      const result = await invoke<string>("git_show_file", {
        projectPath,
        filePath: relativePath,
      });
      originalContent = result;
    } catch {
      originalContent = null;
    }
    fetching = false;
  };

  const analysisPlugin = EditorView.updateListener.of((update) => {
    // Fetch original on first load
    if (originalContent === null && !fetching) {
      fetchOriginal().then(() => {
        if (originalContent !== null) {
          const changes = computeLineChanges(originalContent, update.state.doc.toString());
          update.view.dispatch({
            effects: setGitChanges.of(changes),
          });
        }
      });
    }

    if (!update.docChanged || originalContent === null) return;

    if (debounceTimer) clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      const content = update.state.doc.toString();
      const changes = computeLineChanges(originalContent!, content);
      update.view.dispatch({
        effects: setGitChanges.of(changes),
      });
    }, 300);
  });

  return [
    gitChangesField,
    gitGutterExtension,
    gitGutterTheme,
    analysisPlugin,
  ];
}
