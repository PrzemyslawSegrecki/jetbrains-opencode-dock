import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import './index.css'
import { installJcefHostRepaintCoordinator } from './utils/jcefHostRepaint.ts'
import { installJcefScrollFix } from './utils/jcefScrollFix.ts'
import { ACPBridge } from './utils/bridge.ts'

type ErrorBoundaryProps = {
  children: React.ReactNode;
}

type ErrorBoundaryState = {
  error: Error | null;
}

function reportFrontendDiagnostic(kind: string, details: Record<string, unknown>) {
  try {
    window.__reportFrontendDiagnostic?.(JSON.stringify({
      kind,
      details,
      timestamp: new Date().toISOString(),
      href: window.location.href,
      userAgent: navigator.userAgent,
    }))
  } catch {
    // Best-effort diagnostics only.
  }
}

function normalizeError(value: unknown): { message: string; stack?: string } {
  if (value instanceof Error) {
    return {
      message: value.message,
      stack: value.stack,
    }
  }

  return {
    message: typeof value === 'string' ? value : String(value),
  }
}

function installRuntimeDiagnostics() {
  if ((window as typeof window & { __agentDockRuntimeDiagnosticsInstalled?: boolean }).__agentDockRuntimeDiagnosticsInstalled) {
    return
  }

  (window as typeof window & { __agentDockRuntimeDiagnosticsInstalled?: boolean }).__agentDockRuntimeDiagnosticsInstalled = true

  window.addEventListener('error', (event) => {
    const normalized = normalizeError(event.error ?? event.message)
    reportFrontendDiagnostic('window-error', {
      message: normalized.message,
      stack: normalized.stack,
      source: event.filename,
      line: event.lineno,
      column: event.colno,
    })
  })

  window.addEventListener('unhandledrejection', (event) => {
    const normalized = normalizeError(event.reason)
    reportFrontendDiagnostic('unhandled-rejection', {
      message: normalized.message,
      stack: normalized.stack,
    })
  })
}

class RootErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = {
    error: null,
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    reportFrontendDiagnostic('react-error-boundary', {
      message: error.message,
      stack: error.stack,
      componentStack: info.componentStack,
    })
  }

  private handleReload = () => {
    window.location.reload()
  }

  render() {
    if (!this.state.error) {
      return this.props.children
    }

    return (
      <div className="h-screen bg-background text-foreground flex items-center justify-center p-6">
        <div className="w-full max-w-xl rounded-lg border border-border bg-background-secondary p-5 shadow-sm">
          <div className="text-base font-semibold">Agent Dock crashed</div>
          <p className="mt-2 text-sm text-foreground/80">
            The plugin UI hit an unexpected frontend error. The details were sent to the IDE log.
          </p>
          <pre className="mt-4 max-h-56 overflow-auto rounded border border-border bg-background p-3 text-xs whitespace-pre-wrap break-words">
            {this.state.error.stack || this.state.error.message}
          </pre>
          <div className="mt-4 flex justify-end">
            <button
              className="rounded bg-primary px-3 py-2 text-sm text-primary-foreground"
              onClick={this.handleReload}
              type="button"
            >
              Reload plugin UI
            </button>
          </div>
        </div>
      </div>
    )
  }
}

installJcefHostRepaintCoordinator()
installJcefScrollFix()
installRuntimeDiagnostics()
ACPBridge.initialize()

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <RootErrorBoundary>
      <App />
    </RootErrorBoundary>
  </React.StrictMode>,
)
