import { useMemo } from "react";

interface MarkdownPreviewProps {
  content: string;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function renderMarkdown(md: string): string {
  let html = escapeHtml(md);

  // Code blocks (fenced) — must be before inline transforms
  html = html.replace(
    /```(\w*)\n([\s\S]*?)```/g,
    (_match, lang, code) =>
      `<pre class="md-code-block" data-lang="${lang}"><code>${code.trim()}</code></pre>`,
  );

  // Inline code
  html = html.replace(
    /`([^`]+)`/g,
    '<code class="md-inline-code">$1</code>',
  );

  // Headings
  html = html.replace(/^######\s+(.+)$/gm, '<h6 class="md-h6">$1</h6>');
  html = html.replace(/^#####\s+(.+)$/gm, '<h5 class="md-h5">$1</h5>');
  html = html.replace(/^####\s+(.+)$/gm, '<h4 class="md-h4">$1</h4>');
  html = html.replace(/^###\s+(.+)$/gm, '<h3 class="md-h3">$1</h3>');
  html = html.replace(/^##\s+(.+)$/gm, '<h2 class="md-h2">$1</h2>');
  html = html.replace(/^#\s+(.+)$/gm, '<h1 class="md-h1">$1</h1>');

  // Horizontal rules
  html = html.replace(/^---+$/gm, '<hr class="md-hr" />');

  // Bold + Italic
  html = html.replace(/\*\*\*(.+?)\*\*\*/g, "<strong><em>$1</em></strong>");
  // Bold
  html = html.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
  // Italic
  html = html.replace(/\*(.+?)\*/g, "<em>$1</em>");
  // Strikethrough
  html = html.replace(/~~(.+?)~~/g, "<del>$1</del>");

  // Blockquotes
  html = html.replace(
    /^&gt;\s+(.+)$/gm,
    '<blockquote class="md-blockquote">$1</blockquote>',
  );

  // Unordered lists
  html = html.replace(/^[-*]\s+(.+)$/gm, '<li class="md-li">$1</li>');
  // Wrap consecutive li's in ul
  html = html.replace(
    /(<li class="md-li">[\s\S]*?<\/li>\n?)+/g,
    (match) => `<ul class="md-ul">${match}</ul>`,
  );

  // Ordered lists
  html = html.replace(/^\d+\.\s+(.+)$/gm, '<li class="md-oli">$1</li>');
  html = html.replace(
    /(<li class="md-oli">[\s\S]*?<\/li>\n?)+/g,
    (match) => `<ol class="md-ol">${match}</ol>`,
  );

  // Links (already escaped, so match &quot;)
  html = html.replace(
    /\[([^\]]+)\]\(([^)]+)\)/g,
    '<a class="md-link" href="$2" target="_blank" rel="noopener">$1</a>',
  );

  // Images
  html = html.replace(
    /!\[([^\]]*)\]\(([^)]+)\)/g,
    '<img class="md-img" alt="$1" src="$2" />',
  );

  // Tables (simple)
  html = html.replace(
    /^(\|.+\|)\n(\|[-| :]+\|)\n((?:\|.+\|\n?)+)/gm,
    (_match, header: string, _sep: string, body: string) => {
      const heads = header
        .split("|")
        .filter((c: string) => c.trim())
        .map((c: string) => `<th class="md-th">${c.trim()}</th>`)
        .join("");
      const rows = body
        .trim()
        .split("\n")
        .map((row: string) => {
          const cells = row
            .split("|")
            .filter((c: string) => c.trim())
            .map((c: string) => `<td class="md-td">${c.trim()}</td>`)
            .join("");
          return `<tr>${cells}</tr>`;
        })
        .join("");
      return `<table class="md-table"><thead><tr>${heads}</tr></thead><tbody>${rows}</tbody></table>`;
    },
  );

  // Paragraphs: wrap remaining standalone lines
  html = html.replace(/^(?!<[a-z/])((?!$).+)$/gm, '<p class="md-p">$1</p>');

  return html;
}

export default function MarkdownPreview({ content }: MarkdownPreviewProps) {
  const html = useMemo(() => renderMarkdown(content), [content]);

  return (
    <div className="h-full overflow-auto bg-obsidian-950 p-6">
      <div
        className="md-preview mx-auto max-w-3xl"
        dangerouslySetInnerHTML={{ __html: html }}
      />
      <style>{`
        .md-preview { color: #d1d5db; font-family: Inter, sans-serif; line-height: 1.7; }
        .md-h1 { font-size: 1.875rem; font-weight: 700; color: #f0f0f4; margin: 1.5rem 0 0.75rem; font-family: 'Space Grotesk', sans-serif; border-bottom: 1px solid #2a2a36; padding-bottom: 0.5rem; }
        .md-h2 { font-size: 1.5rem; font-weight: 600; color: #f0f0f4; margin: 1.25rem 0 0.5rem; font-family: 'Space Grotesk', sans-serif; border-bottom: 1px solid #2a2a36; padding-bottom: 0.375rem; }
        .md-h3 { font-size: 1.25rem; font-weight: 600; color: #f0f0f4; margin: 1rem 0 0.5rem; font-family: 'Space Grotesk', sans-serif; }
        .md-h4 { font-size: 1.1rem; font-weight: 600; color: #f0f0f4; margin: 0.75rem 0 0.375rem; }
        .md-h5 { font-size: 1rem; font-weight: 600; color: #f0f0f4; margin: 0.75rem 0 0.375rem; }
        .md-h6 { font-size: 0.9rem; font-weight: 600; color: #b8bfc9; margin: 0.5rem 0 0.25rem; }
        .md-p { margin: 0.5rem 0; }
        .md-code-block { background: #0c0c12; border: 1px solid #2a2a36; border-radius: 6px; padding: 1rem; overflow-x: auto; font-family: 'JetBrains Mono', monospace; font-size: 0.8125rem; line-height: 1.6; margin: 0.75rem 0; color: #f0f0f4; }
        .md-inline-code { background: #1e1e28; border: 1px solid #2a2a36; border-radius: 3px; padding: 0.125rem 0.375rem; font-family: 'JetBrains Mono', monospace; font-size: 0.8125rem; color: #ff8a33; }
        .md-blockquote { border-left: 3px solid #ff6b00; padding-left: 1rem; margin: 0.75rem 0; color: #9ca3af; font-style: italic; }
        .md-ul, .md-ol { padding-left: 1.5rem; margin: 0.5rem 0; }
        .md-li { list-style-type: disc; margin: 0.25rem 0; }
        .md-oli { list-style-type: decimal; margin: 0.25rem 0; }
        .md-link { color: #ff6b00; text-decoration: underline; text-decoration-color: rgba(255,107,0,0.4); }
        .md-link:hover { color: #ff8a33; }
        .md-hr { border: none; border-top: 1px solid #2a2a36; margin: 1.5rem 0; }
        .md-img { max-width: 100%; border-radius: 6px; margin: 0.75rem 0; }
        .md-table { width: 100%; border-collapse: collapse; margin: 0.75rem 0; font-size: 0.875rem; }
        .md-th { text-align: left; padding: 0.5rem 0.75rem; border-bottom: 2px solid #2a2a36; color: #f0f0f4; font-weight: 600; background: #14141c; }
        .md-td { padding: 0.5rem 0.75rem; border-bottom: 1px solid #1e1e28; }
        .md-preview strong { color: #f0f0f4; font-weight: 600; }
        .md-preview em { color: #b8bfc9; }
        .md-preview del { opacity: 0.5; text-decoration: line-through; }
      `}</style>
    </div>
  );
}
