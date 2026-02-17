/**
 * ANSI escape code parser for rendering colored build output.
 * Supports standard 8-color and bright 8-color foreground/background.
 */

import React from "react";

// Standard ANSI color map (foreground codes 30-37)
const ANSI_COLORS: Record<number, string> = {
  30: "#1e1e28", // black
  31: "#ef4444", // red
  32: "#22c55e", // green
  33: "#f0b830", // yellow (forge-gold)
  34: "#3b82f6", // blue
  35: "#a855f7", // magenta
  36: "#06b6d4", // cyan
  37: "#d1d5db", // white
  // Bright variants (90-97)
  90: "#6b7280", // bright black (gray)
  91: "#f87171", // bright red
  92: "#4ade80", // bright green
  93: "#fbbf24", // bright yellow
  94: "#60a5fa", // bright blue
  95: "#c084fc", // bright magenta
  96: "#22d3ee", // bright cyan
  97: "#f0f0f4", // bright white
};

interface AnsiSpan {
  text: string;
  color?: string;
  bgColor?: string;
  bold?: boolean;
  dim?: boolean;
  italic?: boolean;
  underline?: boolean;
}

/** Parse ANSI escape sequences from a string into styled spans. */
export function parseAnsi(text: string): AnsiSpan[] {
  const spans: AnsiSpan[] = [];
  const ansiRegex = /\x1b\[([0-9;]*)m/g;

  let lastIndex = 0;
  let currentColor: string | undefined;
  let currentBg: string | undefined;
  let bold = false;
  let dim = false;
  let italic = false;
  let underline = false;

  let match: RegExpExecArray | null;
  while ((match = ansiRegex.exec(text)) !== null) {
    // Add text before this escape code
    if (match.index > lastIndex) {
      spans.push({
        text: text.slice(lastIndex, match.index),
        color: currentColor,
        bgColor: currentBg,
        bold,
        dim,
        italic,
        underline,
      });
    }
    lastIndex = match.index + match[0].length;

    // Parse the codes
    const codes = match[1].split(";").map(Number);
    for (const code of codes) {
      if (code === 0) {
        // Reset
        currentColor = undefined;
        currentBg = undefined;
        bold = false;
        dim = false;
        italic = false;
        underline = false;
      } else if (code === 1) {
        bold = true;
      } else if (code === 2) {
        dim = true;
      } else if (code === 3) {
        italic = true;
      } else if (code === 4) {
        underline = true;
      } else if (code === 22) {
        bold = false;
        dim = false;
      } else if (code === 23) {
        italic = false;
      } else if (code === 24) {
        underline = false;
      } else if ((code >= 30 && code <= 37) || (code >= 90 && code <= 97)) {
        currentColor = ANSI_COLORS[code];
      } else if (code === 39) {
        currentColor = undefined;
      } else if (code >= 40 && code <= 47) {
        currentBg = ANSI_COLORS[code - 10];
      } else if (code >= 100 && code <= 107) {
        currentBg = ANSI_COLORS[code - 10];
      } else if (code === 49) {
        currentBg = undefined;
      }
    }
  }

  // Add remaining text
  if (lastIndex < text.length) {
    spans.push({
      text: text.slice(lastIndex),
      color: currentColor,
      bgColor: currentBg,
      bold,
      dim,
      italic,
      underline,
    });
  }

  return spans;
}

/** Strip all ANSI escape codes from text. */
export function stripAnsi(text: string): string {
  return text.replace(/\x1b\[[0-9;]*m/g, "");
}

/** Render a line with ANSI coloring as React elements. */
export function AnsiLine({ text }: { text: string }) {
  const spans = parseAnsi(text);

  if (spans.length === 0) return null;

  // If no ANSI codes found (single span with no styling), return plain text
  if (spans.length === 1 && !spans[0].color && !spans[0].bold) {
    return <>{spans[0].text}</>;
  }

  return (
    <>
      {spans.map((span, i) => {
        const style: React.CSSProperties = {};
        if (span.color) style.color = span.color;
        if (span.bgColor) style.backgroundColor = span.bgColor;
        if (span.bold) style.fontWeight = "bold";
        if (span.dim) style.opacity = 0.6;
        if (span.italic) style.fontStyle = "italic";
        if (span.underline) style.textDecoration = "underline";

        if (Object.keys(style).length === 0) {
          return <React.Fragment key={i}>{span.text}</React.Fragment>;
        }

        return (
          <span key={i} style={style}>
            {span.text}
          </span>
        );
      })}
    </>
  );
}
