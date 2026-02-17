use reqwest::Client;
use std::path::PathBuf;

use crate::state::AuthTokens;

/// File-based token storage path: <base_dir>/auth.json
/// This is more reliable than OS keychain because it doesn't depend on
/// code signing or app identity — works across rebuilds and on all platforms.
fn token_path() -> PathBuf {
    let base = crate::state::base_dir();
    base.join("auth.json")
}

#[derive(serde::Serialize, serde::Deserialize)]
struct StoredAuth {
    refresh_token: String,
}

/// Store the MS refresh token to disk.
pub fn store_refresh_token(token: &str) -> Result<(), String> {
    let path = token_path();
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)
            .map_err(|e| format!("Failed to create auth dir: {}", e))?;
    }
    let data = StoredAuth {
        refresh_token: token.to_string(),
    };
    let json = serde_json::to_string_pretty(&data)
        .map_err(|e| format!("Failed to serialize auth: {}", e))?;
    std::fs::write(&path, &json)
        .map_err(|e| format!("Failed to write auth file: {}", e))?;
    log::info!("Refresh token stored to {}", path.display());
    Ok(())
}

/// Load the MS refresh token from disk.
pub fn load_refresh_token() -> Result<Option<String>, String> {
    let path = token_path();
    if !path.exists() {
        log::info!("No stored auth at {}", path.display());
        return Ok(None);
    }
    let json = std::fs::read_to_string(&path)
        .map_err(|e| format!("Failed to read auth file: {}", e))?;
    let data: StoredAuth = serde_json::from_str(&json)
        .map_err(|e| format!("Failed to parse auth file: {}", e))?;
    log::info!("Loaded refresh token from {}", path.display());
    Ok(Some(data.refresh_token))
}

/// Delete the stored refresh token.
pub fn delete_refresh_token() -> Result<(), String> {
    let path = token_path();
    if path.exists() {
        std::fs::remove_file(&path)
            .map_err(|e| format!("Failed to delete auth file: {}", e))?;
    }
    Ok(())
}

/// Attempt to refresh the full auth chain using a stored refresh token.
/// Returns None if no token is stored or refresh fails.
pub async fn try_refresh(client: &Client, client_id: &str) -> Option<AuthTokens> {
    let refresh_token = match load_refresh_token() {
        Ok(Some(token)) => token,
        Ok(None) => {
            log::info!("No stored refresh token — login required");
            return None;
        }
        Err(e) => {
            log::error!("Failed to load refresh token: {}", e);
            return None;
        }
    };

    log::info!("Attempting token refresh...");

    let ms_tokens = match super::microsoft::refresh_tokens(client, client_id, &refresh_token).await
    {
        Ok(t) => t,
        Err(e) => {
            log::error!("MS token refresh failed: {}", e);
            return None;
        }
    };

    // Store new refresh token if provided
    if let Some(ref new_refresh) = ms_tokens.refresh_token {
        if let Err(e) = store_refresh_token(new_refresh) {
            log::error!("Failed to store new refresh token: {}", e);
        }
    }

    let xbox = super::xbox::authenticate_xbox(client, &ms_tokens.access_token)
        .await
        .ok()?;

    let xsts = super::xbox::authenticate_xsts(client, &xbox.token)
        .await
        .ok()?;

    let mc_token =
        super::minecraft::authenticate_minecraft(client, &xsts.user_hash, &xsts.token)
            .await
            .ok()?;

    let profile = super::minecraft::fetch_profile(client, &mc_token)
        .await
        .ok()?;

    log::info!("Auto-login successful: {}", profile.username);

    Some(AuthTokens {
        mc_access_token: mc_token,
        ms_refresh_token: ms_tokens
            .refresh_token
            .unwrap_or_else(|| refresh_token.clone()),
        profile,
    })
}

/// Perform the full login flow: MS code → Xbox → XSTS → MC → Profile.
pub async fn full_login(
    client: &Client,
    ms_access_token: &str,
    ms_refresh_token: &str,
) -> Result<AuthTokens, String> {
    let xbox = super::xbox::authenticate_xbox(client, ms_access_token).await?;
    let xsts = super::xbox::authenticate_xsts(client, &xbox.token).await?;
    let mc_token =
        super::minecraft::authenticate_minecraft(client, &xsts.user_hash, &xsts.token).await?;
    let profile = super::minecraft::fetch_profile(client, &mc_token).await?;

    store_refresh_token(ms_refresh_token)?;

    Ok(AuthTokens {
        mc_access_token: mc_token,
        ms_refresh_token: ms_refresh_token.to_string(),
        profile,
    })
}
