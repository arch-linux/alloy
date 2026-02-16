use portable_pty::{native_pty_system, CommandBuilder, PtySize};
use std::collections::HashMap;
use std::io::{Read, Write};
use std::sync::{Arc, Mutex};
use tauri::{AppHandle, Emitter, State};

pub struct PtyInstance {
    writer: Box<dyn Write + Send>,
    _child: Box<dyn portable_pty::Child + Send + Sync>,
}

pub struct TerminalState {
    pub ptys: Mutex<HashMap<String, PtyInstance>>,
}

impl TerminalState {
    pub fn new() -> Self {
        Self {
            ptys: Mutex::new(HashMap::new()),
        }
    }
}

#[tauri::command]
pub async fn terminal_create(
    id: String,
    cwd: Option<String>,
    app: AppHandle,
    term_state: State<'_, Arc<TerminalState>>,
) -> Result<(), String> {
    let pty_system = native_pty_system();

    let size = PtySize {
        rows: 24,
        cols: 80,
        pixel_width: 0,
        pixel_height: 0,
    };

    let pair = pty_system.openpty(size).map_err(|e| e.to_string())?;

    let mut cmd = CommandBuilder::new_default_prog();
    if let Some(dir) = cwd {
        cmd.cwd(dir);
    }

    let child = pair.slave.spawn_command(cmd).map_err(|e| e.to_string())?;
    let writer = pair.master.take_writer().map_err(|e| e.to_string())?;
    let mut reader = pair.master.try_clone_reader().map_err(|e| e.to_string())?;

    // Store the PTY instance
    {
        let mut ptys = term_state.ptys.lock().map_err(|e| e.to_string())?;
        ptys.insert(
            id.clone(),
            PtyInstance {
                writer,
                _child: child,
            },
        );
    }

    // Spawn a reader thread that emits data to the frontend
    let event_id = id.clone();
    std::thread::spawn(move || {
        let mut buf = [0u8; 4096];
        loop {
            match reader.read(&mut buf) {
                Ok(0) => break,
                Ok(n) => {
                    let data = String::from_utf8_lossy(&buf[..n]).to_string();
                    let _ = app.emit(&format!("terminal:data:{}", event_id), data);
                }
                Err(_) => break,
            }
        }
        let _ = app.emit(&format!("terminal:exit:{}", event_id), ());
    });

    Ok(())
}

#[tauri::command]
pub async fn terminal_write(
    id: String,
    data: String,
    term_state: State<'_, Arc<TerminalState>>,
) -> Result<(), String> {
    let mut ptys = term_state.ptys.lock().map_err(|e| e.to_string())?;
    let pty = ptys.get_mut(&id).ok_or("Terminal not found")?;
    pty.writer
        .write_all(data.as_bytes())
        .map_err(|e| e.to_string())?;
    pty.writer.flush().map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
pub async fn terminal_resize(
    id: String,
    rows: u16,
    cols: u16,
    term_state: State<'_, Arc<TerminalState>>,
) -> Result<(), String> {
    // portable-pty doesn't expose resize on the writer directly
    // The resize is handled by the master PTY, but we'd need to keep a reference.
    // For now, this is a no-op — we'll enhance later if needed.
    let _ = (id, rows, cols, term_state);
    Ok(())
}

#[tauri::command]
pub async fn terminal_destroy(
    id: String,
    term_state: State<'_, Arc<TerminalState>>,
) -> Result<(), String> {
    let mut ptys = term_state.ptys.lock().map_err(|e| e.to_string())?;
    ptys.remove(&id);
    Ok(())
}
