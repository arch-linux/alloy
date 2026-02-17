/**
 * CodeMirror extension for custom fold region markers in Java.
 * Supports //region ... //endregion comments used by IntelliJ and other IDEs.
 */

import { foldService } from "@codemirror/language";
import type { Extension } from "@codemirror/state";

/**
 * Fold service that recognizes //region and //endregion markers.
 * When the cursor is on a //region line, it folds to the matching //endregion.
 */
const regionFold = foldService.of((state, lineStart, lineEnd) => {
  const line = state.doc.lineAt(lineStart);
  const text = line.text.trimStart();

  // Match //region or // region (with optional label)
  if (!/^\/\/\s*region\b/.test(text)) return null;

  // Find the matching //endregion at the same nesting depth
  let depth = 1;
  let pos = line.number + 1;

  while (pos <= state.doc.lines) {
    const nextLine = state.doc.line(pos);
    const nextText = nextLine.text.trimStart();

    if (/^\/\/\s*region\b/.test(nextText)) {
      depth++;
    } else if (/^\/\/\s*endregion\b/.test(nextText)) {
      depth--;
      if (depth === 0) {
        // Fold from end of //region line to start of //endregion line
        return { from: line.to, to: nextLine.from - 1 };
      }
    }
    pos++;
  }

  return null;
});

/**
 * Extension that adds //region ... //endregion fold support for Java files.
 */
export function foldRegions(): Extension {
  return [regionFold];
}
