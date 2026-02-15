use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine};
use rand::RngCore;
use reqwest::Client;
use serde::Deserialize;
use sha2::{Digest, Sha256};
use std::io::{Read, Write};
use url::Url;

const AUTH_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";

#[derive(Debug, Deserialize)]
#[allow(dead_code)]
pub struct MsTokenResponse {
    pub access_token: String,
    pub refresh_token: Option<String>,
    pub expires_in: u64,
}

pub struct PkceChallenge {
    pub verifier: String,
    pub challenge: String,
}

pub fn generate_pkce() -> PkceChallenge {
    let mut bytes = [0u8; 32];
    rand::rng().fill_bytes(&mut bytes);
    let verifier = URL_SAFE_NO_PAD.encode(bytes);

    let mut hasher = Sha256::new();
    hasher.update(verifier.as_bytes());
    let hash = hasher.finalize();
    let challenge = URL_SAFE_NO_PAD.encode(hash);

    PkceChallenge {
        verifier,
        challenge,
    }
}

pub fn build_auth_url(client_id: &str, port: u16, code_challenge: &str) -> String {
    let redirect_uri = format!("http://localhost:{}", port);

    let mut url = Url::parse(AUTH_URL).unwrap();
    url.query_pairs_mut()
        .append_pair("client_id", client_id)
        .append_pair("response_type", "code")
        .append_pair("redirect_uri", &redirect_uri)
        .append_pair("scope", "XboxLive.signin offline_access")
        .append_pair("code_challenge", code_challenge)
        .append_pair("code_challenge_method", "S256")
        .append_pair("response_mode", "query");

    url.to_string()
}

/// Starts a local HTTP server, waits for the OAuth callback, returns the auth code.
pub fn wait_for_callback(port: u16) -> Result<String, String> {
    let listener = std::net::TcpListener::bind(format!("127.0.0.1:{}", port))
        .map_err(|e| format!("Failed to bind local server: {}", e))?;

    listener
        .set_nonblocking(false)
        .map_err(|e| format!("Failed to set blocking: {}", e))?;

    let (mut stream, _) = listener
        .accept()
        .map_err(|e| format!("Failed to accept connection: {}", e))?;

    let mut buf = [0u8; 4096];
    let n = stream
        .read(&mut buf)
        .map_err(|e| format!("Failed to read request: {}", e))?;
    let request = String::from_utf8_lossy(&buf[..n]);

    // Extract the path from the HTTP request line
    let path = request
        .lines()
        .next()
        .and_then(|line| line.split_whitespace().nth(1))
        .ok_or("Invalid HTTP request")?;

    let url = Url::parse(&format!("http://localhost{}", path))
        .map_err(|e| format!("Failed to parse callback URL: {}", e))?;

    // Check for error
    if let Some(error) = url.query_pairs().find(|(k, _)| k == "error") {
        let desc = url
            .query_pairs()
            .find(|(k, _)| k == "error_description")
            .map(|(_, v)| v.to_string())
            .unwrap_or_default();
        let response = format!(
            "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n\
            <html><body style='background:#06060a;color:#d1d5db;font-family:Inter,sans-serif;\
            display:flex;align-items:center;justify-content:center;height:100vh;margin:0'>\
            <div style='text-align:center'><h1 style='color:#ff6b00'>Authentication Failed</h1>\
            <p>{}: {}</p></div></body></html>",
            error.1, desc
        );
        let _ = stream.write_all(response.as_bytes());
        return Err(format!("Auth error: {}: {}", error.1, desc));
    }

    let code = url
        .query_pairs()
        .find(|(k, _)| k == "code")
        .map(|(_, v)| v.to_string())
        .ok_or("No auth code in callback")?;

    let response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n\
        <html><body style='background:#06060a;color:#d1d5db;font-family:Inter,sans-serif;\
        display:flex;align-items:center;justify-content:center;height:100vh;margin:0'>\
        <div style='text-align:center'>\
        <h1 style='color:#ff6b00'>Authentication Successful</h1>\
        <p>You can close this window and return to Alloy Launcher.</p>\
        </div></body></html>";
    let _ = stream.write_all(response.as_bytes());

    Ok(code)
}

/// Exchange auth code for MS access + refresh tokens.
pub async fn exchange_code(
    client: &Client,
    client_id: &str,
    code: &str,
    port: u16,
    code_verifier: &str,
) -> Result<MsTokenResponse, String> {
    let redirect_uri = format!("http://localhost:{}", port);

    let resp = client
        .post(TOKEN_URL)
        .form(&[
            ("client_id", client_id),
            ("scope", "XboxLive.signin offline_access"),
            ("code", code),
            ("redirect_uri", &redirect_uri),
            ("grant_type", "authorization_code"),
            ("code_verifier", code_verifier),
        ])
        .send()
        .await
        .map_err(|e| format!("Token exchange request failed: {}", e))?;

    if !resp.status().is_success() {
        let body = resp.text().await.unwrap_or_default();
        return Err(format!("Token exchange failed: {}", body));
    }

    resp.json::<MsTokenResponse>()
        .await
        .map_err(|e| format!("Failed to parse token response: {}", e))
}

/// Refresh MS tokens using a refresh token.
pub async fn refresh_tokens(
    client: &Client,
    client_id: &str,
    refresh_token: &str,
) -> Result<MsTokenResponse, String> {
    let resp = client
        .post(TOKEN_URL)
        .form(&[
            ("client_id", client_id),
            ("scope", "XboxLive.signin offline_access"),
            ("refresh_token", refresh_token),
            ("grant_type", "refresh_token"),
        ])
        .send()
        .await
        .map_err(|e| format!("Token refresh request failed: {}", e))?;

    if !resp.status().is_success() {
        let body = resp.text().await.unwrap_or_default();
        return Err(format!("Token refresh failed: {}", body));
    }

    resp.json::<MsTokenResponse>()
        .await
        .map_err(|e| format!("Failed to parse refresh response: {}", e))
}
