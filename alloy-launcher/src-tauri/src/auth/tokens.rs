use keyring::Entry;
use reqwest::Client;

use crate::state::AuthTokens;

const SERVICE_NAME: &str = "alloy-launcher";
const REFRESH_KEY: &str = "ms-refresh-token";

/// Store the MS refresh token in the OS keychain.
pub fn store_refresh_token(token: &str) -> Result<(), String> {
    let entry = Entry::new(SERVICE_NAME, REFRESH_KEY)
        .map_err(|e| format!("Keyring error: {}", e))?;
    entry
        .set_password(token)
        .map_err(|e| format!("Failed to store token: {}", e))
}

/// Load the MS refresh token from the OS keychain.
pub fn load_refresh_token() -> Result<Option<String>, String> {
    let entry = Entry::new(SERVICE_NAME, REFRESH_KEY)
        .map_err(|e| format!("Keyring error: {}", e))?;
    match entry.get_password() {
        Ok(token) => Ok(Some(token)),
        Err(keyring::Error::NoEntry) => Ok(None),
        Err(e) => Err(format!("Failed to load token: {}", e)),
    }
}

/// Delete the stored refresh token.
pub fn delete_refresh_token() -> Result<(), String> {
    let entry = Entry::new(SERVICE_NAME, REFRESH_KEY)
        .map_err(|e| format!("Keyring error: {}", e))?;
    match entry.delete_credential() {
        Ok(()) => Ok(()),
        Err(keyring::Error::NoEntry) => Ok(()),
        Err(e) => Err(format!("Failed to delete token: {}", e)),
    }
}

/// Attempt to refresh the full auth chain using a stored refresh token.
/// Returns None if no token is stored or refresh fails.
pub async fn try_refresh(client: &Client, client_id: &str) -> Option<AuthTokens> {
    let refresh_token = load_refresh_token().ok().flatten()?;

    let ms_tokens = super::microsoft::refresh_tokens(client, client_id, &refresh_token)
        .await
        .ok()?;

    // Store new refresh token if provided
    if let Some(ref new_refresh) = ms_tokens.refresh_token {
        let _ = store_refresh_token(new_refresh);
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
