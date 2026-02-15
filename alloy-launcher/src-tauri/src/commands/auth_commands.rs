use crate::auth::{microsoft, tokens};
use crate::state::{AppState, MinecraftProfile};
use tauri::State;

#[derive(serde::Serialize)]
pub struct AuthResult {
    pub success: bool,
    pub profile: Option<MinecraftProfile>,
    pub error: Option<String>,
}

/// Start the full Microsoft OAuth login flow.
#[tauri::command]
pub async fn login(state: State<'_, AppState>) -> Result<AuthResult, String> {
    let client_id = {
        let settings = state.settings.lock().unwrap();
        settings.client_id.clone()
    };

    // Generate PKCE
    let pkce = microsoft::generate_pkce();

    // Bind local server to a random port
    let listener = std::net::TcpListener::bind("127.0.0.1:0")
        .map_err(|e| format!("Failed to bind local server: {}", e))?;
    let port = listener
        .local_addr()
        .map_err(|e| format!("Failed to get port: {}", e))?
        .port();
    drop(listener); // Release so wait_for_callback can bind

    // Build auth URL and open browser
    let auth_url = microsoft::build_auth_url(&client_id, port, &pkce.challenge);
    open::that(&auth_url).map_err(|e| format!("Failed to open browser: {}", e))?;

    // Wait for callback (blocking — runs on Tauri's async thread pool)
    let code = tokio::task::spawn_blocking(move || microsoft::wait_for_callback(port))
        .await
        .map_err(|e| format!("Callback task failed: {}", e))?
        .map_err(|e| format!("Auth callback failed: {}", e))?;

    // Exchange code for tokens
    let ms_tokens =
        microsoft::exchange_code(&state.http_client, &client_id, &code, port, &pkce.verifier)
            .await?;

    let refresh_token = ms_tokens
        .refresh_token
        .clone()
        .ok_or("No refresh token received")?;

    // Full chain: Xbox → XSTS → MC → Profile
    let auth = tokens::full_login(
        &state.http_client,
        &ms_tokens.access_token,
        &refresh_token,
    )
    .await?;

    let profile = auth.profile.clone();
    *state.auth.lock().unwrap() = Some(auth);

    Ok(AuthResult {
        success: true,
        profile: Some(profile),
        error: None,
    })
}

/// Check if we have a valid session (try refreshing stored tokens).
#[tauri::command]
pub async fn check_auth(state: State<'_, AppState>) -> Result<AuthResult, String> {
    // Already authenticated in this session
    {
        let auth = state.auth.lock().unwrap();
        if let Some(ref a) = *auth {
            return Ok(AuthResult {
                success: true,
                profile: Some(a.profile.clone()),
                error: None,
            });
        }
    }

    let client_id = {
        let settings = state.settings.lock().unwrap();
        settings.client_id.clone()
    };

    // Try to refresh from stored token
    match tokens::try_refresh(&state.http_client, &client_id).await {
        Some(auth) => {
            let profile = auth.profile.clone();
            *state.auth.lock().unwrap() = Some(auth);
            Ok(AuthResult {
                success: true,
                profile: Some(profile),
                error: None,
            })
        }
        None => Ok(AuthResult {
            success: false,
            profile: None,
            error: None,
        }),
    }
}

/// Log out: clear stored tokens and auth state.
#[tauri::command]
pub async fn logout(state: State<'_, AppState>) -> Result<(), String> {
    *state.auth.lock().unwrap() = None;
    tokens::delete_refresh_token()?;
    Ok(())
}
