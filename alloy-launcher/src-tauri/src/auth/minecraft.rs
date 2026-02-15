use reqwest::Client;
use serde::{Deserialize, Serialize};

use crate::state::MinecraftProfile;

const MC_AUTH_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";

#[derive(Debug, Serialize)]
struct McAuthRequest {
    #[serde(rename = "identityToken")]
    identity_token: String,
}

#[derive(Debug, Deserialize)]
struct McAuthResponse {
    access_token: String,
}

#[derive(Debug, Deserialize)]
struct McProfileResponse {
    id: String,
    name: String,
    skins: Option<Vec<McSkin>>,
}

#[derive(Debug, Deserialize)]
struct McSkin {
    url: String,
}

/// Exchange XSTS token for a Minecraft access token.
pub async fn authenticate_minecraft(
    client: &Client,
    user_hash: &str,
    xsts_token: &str,
) -> Result<String, String> {
    let body = McAuthRequest {
        identity_token: format!("XBL3.0 x={};{}", user_hash, xsts_token),
    };

    let resp = client
        .post(MC_AUTH_URL)
        .json(&body)
        .send()
        .await
        .map_err(|e| format!("MC auth request failed: {}", e))?;

    if !resp.status().is_success() {
        let text = resp.text().await.unwrap_or_default();
        return Err(format!("MC auth failed: {}", text));
    }

    let data: McAuthResponse = resp
        .json()
        .await
        .map_err(|e| format!("Failed to parse MC auth response: {}", e))?;

    Ok(data.access_token)
}

/// Fetch the Minecraft profile (username, UUID, skin) using an MC access token.
pub async fn fetch_profile(
    client: &Client,
    mc_access_token: &str,
) -> Result<MinecraftProfile, String> {
    let resp = client
        .get(MC_PROFILE_URL)
        .header("Authorization", format!("Bearer {}", mc_access_token))
        .send()
        .await
        .map_err(|e| format!("Profile fetch failed: {}", e))?;

    if !resp.status().is_success() {
        let status = resp.status();
        let text = resp.text().await.unwrap_or_default();
        if status.as_u16() == 404 {
            return Err("This Microsoft account does not own Minecraft. Purchase the game at minecraft.net.".to_string());
        }
        return Err(format!("Profile fetch failed ({}): {}", status, text));
    }

    let data: McProfileResponse = resp
        .json()
        .await
        .map_err(|e| format!("Failed to parse profile: {}", e))?;

    let skin_url = data
        .skins
        .and_then(|s| s.into_iter().next().map(|skin| skin.url));

    // Format UUID with dashes
    let uuid = if data.id.len() == 32 {
        format!(
            "{}-{}-{}-{}-{}",
            &data.id[..8],
            &data.id[8..12],
            &data.id[12..16],
            &data.id[16..20],
            &data.id[20..]
        )
    } else {
        data.id
    };

    Ok(MinecraftProfile {
        username: data.name,
        uuid,
        skin_url,
    })
}
