import { Component } from "react";
import type { ReactNode } from "react";
import { BrowserRouter, Routes, Route, Navigate } from "react-router";
import WelcomePage from "./pages/WelcomePage";
import EditorPage from "./pages/EditorPage";

class ErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state: { error: Error | null } = { error: null };
  static getDerivedStateFromError(error: Error) {
    return { error };
  }
  render() {
    if (this.state.error) {
      return (
        <div style={{ background: "#1a1a1a", color: "#ff6b6b", padding: 32, fontFamily: "monospace", whiteSpace: "pre-wrap", height: "100vh", overflow: "auto" }}>
          <h1 style={{ color: "#ff6b6b", fontSize: 18 }}>React Crash Caught</h1>
          <p style={{ color: "#ffa07a", marginTop: 12 }}>{this.state.error.message}</p>
          <pre style={{ color: "#ccc", marginTop: 16, fontSize: 12 }}>{this.state.error.stack}</pre>
          <button onClick={() => this.setState({ error: null })} style={{ marginTop: 16, padding: "8px 16px", background: "#333", color: "#fff", border: "1px solid #555", cursor: "pointer" }}>
            Try Again
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

export default function App() {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<WelcomePage />} />
          <Route path="/editor" element={<EditorPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </ErrorBoundary>
  );
}
