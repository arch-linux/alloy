import { EditorView } from "@codemirror/view";
import { HighlightStyle, syntaxHighlighting } from "@codemirror/language";
import { tags } from "@lezer/highlight";

export const alloyTheme = EditorView.theme(
  {
    "&": {
      backgroundColor: "#06060a",
      color: "#f0f0f4",
      fontSize: "13px",
    },
    ".cm-content": {
      caretColor: "#ff6b00",
      fontFamily: '"JetBrains Mono", monospace',
      padding: "4px 0",
    },
    ".cm-cursor, .cm-dropCursor": {
      borderLeftColor: "#ff6b00",
      borderLeftWidth: "2px",
    },
    "&.cm-focused .cm-selectionBackground, .cm-selectionBackground, .cm-content ::selection":
      {
        backgroundColor: "rgba(255, 107, 0, 0.15)",
      },
    ".cm-activeLine": {
      backgroundColor: "#0c0c12",
    },
    ".cm-gutters": {
      backgroundColor: "#0c0c12",
      color: "#6b7280",
      borderRight: "1px solid #1e1e28",
    },
    ".cm-activeLineGutter": {
      backgroundColor: "#14141c",
      color: "#9ca3af",
    },
    ".cm-lineNumbers .cm-gutterElement": {
      padding: "0 8px 0 16px",
    },
    ".cm-foldGutter": {
      color: "#3a3a48",
    },
    ".cm-foldGutter:hover": {
      color: "#9ca3af",
    },
    "&.cm-focused .cm-matchingBracket": {
      backgroundColor: "rgba(255, 107, 0, 0.3)",
      outline: "none",
    },
    ".cm-searchMatch": {
      backgroundColor: "rgba(240, 184, 48, 0.2)",
    },
    ".cm-searchMatch.cm-searchMatch-selected": {
      backgroundColor: "rgba(240, 184, 48, 0.35)",
    },
    ".cm-selectionMatch": {
      backgroundColor: "rgba(255, 107, 0, 0.1)",
    },
    ".cm-tooltip": {
      backgroundColor: "#14141c",
      border: "1px solid #2a2a36",
      color: "#f0f0f4",
    },
    ".cm-tooltip-autocomplete": {
      "& > ul > li": {
        padding: "2px 8px",
      },
      "& > ul > li[aria-selected]": {
        backgroundColor: "rgba(255, 107, 0, 0.15)",
        color: "#f0f0f4",
      },
    },
    ".cm-panels": {
      backgroundColor: "#0c0c12",
      color: "#d1d5db",
    },
    ".cm-panel.cm-search": {
      backgroundColor: "#14141c",
      padding: "6px 10px",
      borderBottom: "1px solid #2a2a36",
    },
    ".cm-panel.cm-search input, .cm-panel.cm-search select": {
      backgroundColor: "#0c0c12",
      color: "#f0f0f4",
      border: "1px solid #2a2a36",
      borderRadius: "3px",
      padding: "2px 6px",
      fontSize: "12px",
      outline: "none",
    },
    ".cm-panel.cm-search input:focus": {
      borderColor: "#ff6b00",
    },
    ".cm-panel.cm-search button": {
      color: "#d1d5db",
      fontSize: "12px",
      borderRadius: "3px",
      padding: "2px 8px",
    },
    ".cm-panel.cm-search button:hover": {
      backgroundColor: "#1e1e28",
      color: "#f0f0f4",
    },
    ".cm-panel.cm-search label": {
      color: "#9ca3af",
      fontSize: "12px",
    },
    ".cm-panel.cm-search [name=close]": {
      color: "#6b7280",
    },
    ".cm-panel.cm-search [name=close]:hover": {
      color: "#f0f0f4",
    },
  },
  { dark: true },
);

const alloyHighlightStyle = HighlightStyle.define([
  { tag: tags.keyword, color: "#ff6b00" },
  { tag: tags.controlKeyword, color: "#ff6b00" },
  { tag: tags.operatorKeyword, color: "#ff6b00" },
  { tag: tags.definitionKeyword, color: "#ff6b00" },
  { tag: tags.moduleKeyword, color: "#ff6b00" },
  { tag: tags.string, color: "#f0b830" },
  { tag: tags.regexp, color: "#f0b830" },
  { tag: tags.comment, color: "#6b7280", fontStyle: "italic" },
  { tag: tags.blockComment, color: "#6b7280", fontStyle: "italic" },
  { tag: tags.lineComment, color: "#6b7280", fontStyle: "italic" },
  { tag: tags.docComment, color: "#6b7280", fontStyle: "italic" },
  { tag: tags.typeName, color: "#f0f0f4", fontWeight: "bold" },
  { tag: tags.className, color: "#f0f0f4", fontWeight: "bold" },
  { tag: tags.definition(tags.typeName), color: "#f0f0f4", fontWeight: "bold" },
  { tag: tags.function(tags.variableName), color: "#ff8a33" },
  { tag: tags.definition(tags.function(tags.variableName)), color: "#ff8a33" },
  { tag: tags.number, color: "#ff4400" },
  { tag: tags.integer, color: "#ff4400" },
  { tag: tags.float, color: "#ff4400" },
  { tag: tags.bool, color: "#ff6b00" },
  { tag: tags.null, color: "#ff6b00" },
  { tag: tags.variableName, color: "#d1d5db" },
  { tag: tags.propertyName, color: "#d1d5db" },
  { tag: tags.operator, color: "#b8bfc9" },
  { tag: tags.punctuation, color: "#9ca3af" },
  { tag: tags.bracket, color: "#9ca3af" },
  { tag: tags.meta, color: "#f0b830" },
  { tag: tags.annotation, color: "#f0b830" },
  { tag: tags.tagName, color: "#ff6b00" },
  { tag: tags.attributeName, color: "#ff8a33" },
  { tag: tags.attributeValue, color: "#f0b830" },
  { tag: tags.heading, color: "#ff6b00", fontWeight: "bold" },
  { tag: tags.link, color: "#ff8a33", textDecoration: "underline" },
  { tag: tags.escape, color: "#ff4400" },
  { tag: tags.invalid, color: "#ff4400", textDecoration: "underline wavy" },
]);

export const alloyHighlight = syntaxHighlighting(alloyHighlightStyle);
