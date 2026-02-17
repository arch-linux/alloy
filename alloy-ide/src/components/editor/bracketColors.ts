/**
 * Rainbow bracket pair colorization for CodeMirror 6.
 * Colors matching brackets with distinct colors based on nesting depth.
 */

import { EditorView, Decoration } from "@codemirror/view";
import { syntaxTree } from "@codemirror/language";
import { StateField, RangeSet } from "@codemirror/state";
import type { DecorationSet } from "@codemirror/view";
import type { Extension } from "@codemirror/state";

// Bracket colors cycling through Alloy's palette
const BRACKET_COLORS = [
  "#ff6b00", // ember
  "#f0b830", // forge-gold
  "#3b82f6", // blue
  "#22c55e", // green
  "#a855f7", // purple
  "#f43f5e", // rose
];

function bracketMark(depth: number) {
  const color = BRACKET_COLORS[depth % BRACKET_COLORS.length];
  return Decoration.mark({
    attributes: { style: `color: ${color}; font-weight: 500` },
  });
}

const OPEN_BRACKETS = new Set(["(", "[", "{"]);
const CLOSE_BRACKETS = new Set([")", "]", "}"]);
const BRACKET_PAIRS: Record<string, string> = {
  "(": ")",
  "[": "]",
  "{": "}",
};

const bracketField = StateField.define<DecorationSet>({
  create(state) {
    return computeBracketDecorations(state.doc.toString());
  },
  update(value, tr) {
    if (!tr.docChanged) return value;
    return computeBracketDecorations(tr.state.doc.toString());
  },
  provide: (f) => EditorView.decorations.from(f),
});

function computeBracketDecorations(text: string): DecorationSet {
  const decos: { from: number; to: number; deco: Decoration }[] = [];
  const stack: { char: string; pos: number; depth: number }[] = [];
  let depth = 0;

  // Track string/comment state for Java-like languages
  let inString = false;
  let stringChar = "";
  let inLineComment = false;
  let inBlockComment = false;

  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    const next = text[i + 1] || "";

    // Handle newlines
    if (ch === "\n") {
      inLineComment = false;
      continue;
    }

    // Handle block comment end
    if (inBlockComment) {
      if (ch === "*" && next === "/") {
        inBlockComment = false;
        i++;
      }
      continue;
    }

    // Handle line comment
    if (inLineComment) continue;

    // Handle string content
    if (inString) {
      if (ch === "\\" && i + 1 < text.length) {
        i++; // Skip escaped character
        continue;
      }
      if (ch === stringChar) {
        inString = false;
      }
      continue;
    }

    // Check for comment start
    if (ch === "/" && next === "/") {
      inLineComment = true;
      i++;
      continue;
    }
    if (ch === "/" && next === "*") {
      inBlockComment = true;
      i++;
      continue;
    }

    // Check for string start
    if (ch === '"' || ch === "'") {
      inString = true;
      stringChar = ch;
      continue;
    }

    // Process brackets
    if (OPEN_BRACKETS.has(ch)) {
      decos.push({
        from: i,
        to: i + 1,
        deco: bracketMark(depth),
      });
      stack.push({ char: ch, pos: i, depth });
      depth++;
    } else if (CLOSE_BRACKETS.has(ch)) {
      depth = Math.max(0, depth - 1);
      // Find matching open bracket
      const expectedOpen = Object.entries(BRACKET_PAIRS).find(
        ([, v]) => v === ch,
      )?.[0];
      const lastOpen = stack.length > 0 ? stack[stack.length - 1] : null;

      if (lastOpen && lastOpen.char === expectedOpen) {
        stack.pop();
        decos.push({
          from: i,
          to: i + 1,
          deco: bracketMark(lastOpen.depth),
        });
      } else {
        // Mismatched bracket - show in red
        decos.push({
          from: i,
          to: i + 1,
          deco: Decoration.mark({
            attributes: { style: "color: #ef4444; font-weight: 700; text-decoration: underline wavy #ef4444" },
          }),
        });
      }
    }
  }

  // Mark unmatched open brackets
  for (const unmatched of stack) {
    // Already decorated when we pushed, but we could highlight them as errors
    // For now, they're colored by depth which is fine
  }

  // Sort by position for RangeSet
  decos.sort((a, b) => a.from - b.from || a.to - b.to);

  return RangeSet.of(decos.map((d) => d.deco.range(d.from, d.to)));
}

/**
 * Extension for rainbow bracket colorization.
 */
export function bracketColors(): Extension {
  return [bracketField];
}
