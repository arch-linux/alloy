use reqwest::Client;
use serde::{Deserialize, Serialize};

const XBOX_AUTH_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_AUTH_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";

#[derive(Debug, Serialize)]
struct XboxAuthRequest {
    #[serde(rename = "Properties")]
    properties: XboxAuthProperties,
    #[serde(rename = "RelyingParty")]
    relying_party: String,
    #[serde(rename = "TokenType")]
    token_type: String,
}

#[derive(Debug, Serialize)]
struct XboxAuthProperties {
    #[serde(rename = "AuthMethod")]
    auth_method: String,
    #[serde(rename = "SiteName")]
    site_name: String,
    #[serde(rename = "RpsTicket")]
    rps_ticket: String,
}

#[derive(Debug, Serialize)]
struct XstsRequest {
    #[serde(rename = "Properties")]
    properties: XstsProperties,
    #[serde(rename = "RelyingParty")]
    relying_party: String,
    #[serde(rename = "TokenType")]
    token_type: String,
}

#[derive(Debug, Serialize)]
struct XstsProperties {
    #[serde(rename = "SandboxId")]
    sandbox_id: String,
    #[serde(rename = "UserTokens")]
    user_tokens: Vec<String>,
}

#[derive(Debug, Deserialize)]
struct XboxAuthResponse {
    #[serde(rename = "Token")]
    token: String,
    #[serde(rename = "DisplayClaims")]
    display_claims: DisplayClaims,
}

#[derive(Debug, Deserialize)]
struct DisplayClaims {
    xui: Vec<XuiEntry>,
}

#[derive(Debug, Deserialize)]
struct XuiEntry {
    uhs: String,
}

pub struct XboxTokens {
    pub token: String,
    pub user_hash: String,
}

/// Authenticate with Xbox Live using an MS access token.
pub async fn authenticate_xbox(
    client: &Client,
    ms_access_token: &str,
) -> Result<XboxTokens, String> {
    let body = XboxAuthRequest {
        properties: XboxAuthProperties {
            auth_method: "RPS".to_string(),
            site_name: "user.auth.xboxlive.com".to_string(),
            rps_ticket: format!("d={}", ms_access_token),
        },
        relying_party: "http://auth.xboxlive.com".to_string(),
        token_type: "JWT".to_string(),
    };

    let resp = client
        .post(XBOX_AUTH_URL)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .json(&body)
        .send()
        .await
        .map_err(|e| format!("Xbox auth request failed: {}", e))?;

    if !resp.status().is_success() {
        let status = resp.status();
        let text = resp.text().await.unwrap_or_default();
        return Err(format!("Xbox auth failed ({}): {}", status, text));
    }

    let data: XboxAuthResponse = resp
        .json()
        .await
        .map_err(|e| format!("Failed to parse Xbox response: {}", e))?;

    let user_hash = data
        .display_claims
        .xui
        .first()
        .ok_or("No user hash in Xbox response")?
        .uhs
        .clone();

    Ok(XboxTokens {
        token: data.token,
        user_hash,
    })
}

/// Exchange Xbox token for XSTS token.
pub async fn authenticate_xsts(
    client: &Client,
    xbox_token: &str,
) -> Result<XboxTokens, String> {
    let body = XstsRequest {
        properties: XstsProperties {
            sandbox_id: "RETAIL".to_string(),
            user_tokens: vec![xbox_token.to_string()],
        },
        relying_party: "rp://api.minecraftservices.com/".to_string(),
        token_type: "JWT".to_string(),
    };

    let resp = client
        .post(XSTS_AUTH_URL)
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .json(&body)
        .send()
        .await
        .map_err(|e| format!("XSTS auth request failed: {}", e))?;

    if !resp.status().is_success() {
        let status = resp.status();
        let text = resp.text().await.unwrap_or_default();

        // Check for specific XSTS errors
        if text.contains("2148916233") {
            return Err("This Microsoft account does not have an Xbox account. Please sign up for Xbox at xbox.com first.".to_string());
        }
        if text.contains("2148916238") {
            return Err("This account belongs to someone under 18. An adult must add the account to a Family group.".to_string());
        }

        return Err(format!("XSTS auth failed ({}): {}", status, text));
    }

    let data: XboxAuthResponse = resp
        .json()
        .await
        .map_err(|e| format!("Failed to parse XSTS response: {}", e))?;

    let user_hash = data
        .display_claims
        .xui
        .first()
        .ok_or("No user hash in XSTS response")?
        .uhs
        .clone();

    Ok(XboxTokens {
        token: data.token,
        user_hash,
    })
}
