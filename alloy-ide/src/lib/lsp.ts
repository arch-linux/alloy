import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

export interface LspCompletionItem {
  label: string;
  kind: string;
  detail: string | null;
  insert_text: string | null;
  sort_text: string | null;
}

export interface LspHoverResult {
  contents: string;
}

export interface LspLocation {
  uri: string;
  line: number;
  character: number;
}

export interface LspDiagnostic {
  path: string;
  line: number;
  character: number;
  end_line: number;
  end_character: number;
  severity: string;
  message: string;
  source: string | null;
}

/** Start the LSP server for a project. */
export async function lspStart(projectPath: string): Promise<void> {
  await invoke("lsp_start", { projectPath });
}

/** Stop the LSP server. */
export async function lspStop(): Promise<void> {
  await invoke("lsp_stop");
}

/** Get the current LSP status. */
export async function lspStatus(): Promise<string> {
  return await invoke("lsp_status");
}

/** Notify LSP that a file was opened. */
export async function lspDidOpen(
  path: string,
  languageId: string,
  text: string
): Promise<void> {
  await invoke("lsp_did_open", { path, languageId, text });
}

/** Notify LSP that a file changed. */
export async function lspDidChange(
  path: string,
  version: number,
  text: string
): Promise<void> {
  await invoke("lsp_did_change", { path, version, text });
}

/** Notify LSP that a file was closed. */
export async function lspDidClose(path: string): Promise<void> {
  await invoke("lsp_did_close", { path });
}

/** Notify LSP that a file was saved. */
export async function lspDidSave(path: string, text: string): Promise<void> {
  await invoke("lsp_did_save", { path, text });
}

/** Get completions at a position. */
export async function lspCompletion(
  path: string,
  line: number,
  character: number
): Promise<LspCompletionItem[]> {
  return await invoke("lsp_completion", { path, line, character });
}

/** Get hover info at a position. */
export async function lspHover(
  path: string,
  line: number,
  character: number
): Promise<LspHoverResult | null> {
  return await invoke("lsp_hover", { path, line, character });
}

/** Go to definition. */
export async function lspDefinition(
  path: string,
  line: number,
  character: number
): Promise<LspLocation[]> {
  return await invoke("lsp_definition", { path, line, character });
}

/** Find references. */
export async function lspReferences(
  path: string,
  line: number,
  character: number
): Promise<LspLocation[]> {
  return await invoke("lsp_references", { path, line, character });
}

export interface LspTextEdit {
  start_line: number;
  start_character: number;
  end_line: number;
  end_character: number;
  new_text: string;
}

export interface LspWorkspaceEdit {
  path: string;
  edits: LspTextEdit[];
}

/** Prepare rename — check if rename is valid at position. */
export async function lspPrepareRename(
  path: string,
  line: number,
  character: number
): Promise<string | null> {
  return await invoke("lsp_prepare_rename", { path, line, character });
}

/** Rename a symbol across the workspace. */
export async function lspRename(
  path: string,
  line: number,
  character: number,
  newName: string
): Promise<LspWorkspaceEdit[]> {
  return await invoke("lsp_rename", { path, line, character, newName });
}

export interface LspWorkspaceSymbol {
  name: string;
  kind: string;
  path: string;
  line: number;
  character: number;
  container_name: string | null;
}

/** Search workspace symbols. */
export async function lspWorkspaceSymbols(
  query: string
): Promise<LspWorkspaceSymbol[]> {
  return await invoke("lsp_workspace_symbols", { query });
}

export interface LspSignatureHelp {
  signatures: LspSignatureInfo[];
  active_signature: number;
  active_parameter: number;
}

export interface LspSignatureInfo {
  label: string;
  parameters: string[];
}

/** Get signature help for method parameters. */
export async function lspSignatureHelp(
  path: string,
  line: number,
  character: number
): Promise<LspSignatureHelp | null> {
  return await invoke("lsp_signature_help", { path, line, character });
}

export interface LspDocumentSymbol {
  name: string;
  kind: string;
  line: number;
  character: number;
  end_line: number;
  children: LspDocumentSymbol[];
}

/** Get document symbols for outline view. */
export async function lspDocumentSymbols(
  path: string
): Promise<LspDocumentSymbol[]> {
  return await invoke("lsp_document_symbols", { path });
}

export interface LspCodeAction {
  title: string;
  kind: string | null;
  edits: LspWorkspaceEdit[];
}

/** Get code actions at a range. */
export async function lspCodeActions(
  path: string,
  startLine: number,
  startCharacter: number,
  endLine: number,
  endCharacter: number
): Promise<LspCodeAction[]> {
  return await invoke("lsp_code_actions", {
    path,
    startLine,
    startCharacter,
    endLine,
    endCharacter,
  });
}

/** Listen for diagnostics from the LSP server. */
export function onLspDiagnostics(
  callback: (diagnostics: LspDiagnostic[]) => void
): Promise<() => void> {
  return listen<LspDiagnostic[]>("lsp:diagnostics", (event) => {
    callback(event.payload);
  }).then((unlisten) => unlisten);
}

/** Listen for LSP status changes. */
export function onLspStatus(
  callback: (status: string) => void
): Promise<() => void> {
  return listen<string>("lsp:status", (event) => {
    callback(event.payload);
  }).then((unlisten) => unlisten);
}
