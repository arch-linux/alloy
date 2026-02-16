use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

#[derive(Debug, Deserialize)]
pub struct ImportAssetArgs {
    /// Absolute path to the source image file
    pub source_path: String,
    /// Type of asset: "block", "item", "gui_element", "entity", "particle"
    pub asset_type: String,
    /// The mod project root directory
    pub project_path: String,
    /// The mod ID (from alloy.mod.json)
    pub mod_id: String,
    /// Name for the asset (e.g. "copper_ore" for a block texture)
    pub asset_name: String,
    /// Whether to generate registration code
    pub generate_code: bool,
}

#[derive(Debug, Serialize)]
pub struct ImportResult {
    /// Files that were created
    pub created_files: Vec<CreatedFile>,
    /// Registration code that was generated (if any)
    pub registration_code: Option<String>,
    /// The destination texture path
    pub texture_path: String,
}

#[derive(Debug, Serialize)]
pub struct CreatedFile {
    pub path: String,
    pub file_type: String,
}

#[derive(Debug, Serialize)]
pub struct AssetInfo {
    pub width: u32,
    pub height: u32,
    pub file_size: u64,
    pub suggested_type: String,
    pub suggested_name: String,
}

#[tauri::command]
pub async fn analyze_image(path: String) -> Result<AssetInfo, String> {
    let file_path = Path::new(&path);
    if !file_path.exists() {
        return Err("File does not exist".to_string());
    }

    let metadata = fs::metadata(&path).map_err(|e| format!("Failed to read file: {}", e))?;
    let file_size = metadata.len();

    // Read PNG header to get dimensions
    let data = fs::read(&path).map_err(|e| format!("Failed to read file: {}", e))?;
    let (width, height) = read_png_dimensions(&data)
        .unwrap_or((0, 0));

    // Suggest asset type based on dimensions
    let suggested_type = if width == height && (width == 16 || width == 32 || width == 64 || width == 128) {
        // Square, power-of-two → likely block or item texture
        if width >= 64 {
            "entity".to_string()
        } else {
            "block".to_string()
        }
    } else if width == 256 && height == 256 {
        "gui_element".to_string()
    } else if height > width * 2 {
        // Tall → likely sprite sheet
        "particle".to_string()
    } else {
        "item".to_string()
    };

    // Suggest name from filename
    let suggested_name = file_path
        .file_stem()
        .and_then(|n| n.to_str())
        .unwrap_or("unnamed")
        .to_lowercase()
        .replace(|c: char| !c.is_alphanumeric() && c != '_', "_");

    Ok(AssetInfo {
        width,
        height,
        file_size,
        suggested_type,
        suggested_name,
    })
}

#[tauri::command]
pub async fn import_asset(args: ImportAssetArgs) -> Result<ImportResult, String> {
    let project = Path::new(&args.project_path);
    let source = Path::new(&args.source_path);

    if !source.exists() {
        return Err("Source file does not exist".to_string());
    }
    if !project.exists() {
        return Err("Project directory does not exist".to_string());
    }

    let mut created_files = Vec::new();

    // Determine texture destination path
    let texture_subdir = match args.asset_type.as_str() {
        "block" => "block",
        "item" => "item",
        "gui_element" => "gui",
        "entity" => "entity",
        "particle" => "particle",
        _ => return Err(format!("Unknown asset type: {}", args.asset_type)),
    };

    let textures_dir = project
        .join("src/main/resources/assets")
        .join(&args.mod_id)
        .join("textures")
        .join(texture_subdir);

    fs::create_dir_all(&textures_dir)
        .map_err(|e| format!("Failed to create textures directory: {}", e))?;

    let dest_texture = textures_dir.join(format!("{}.png", args.asset_name));
    fs::copy(source, &dest_texture)
        .map_err(|e| format!("Failed to copy texture: {}", e))?;

    created_files.push(CreatedFile {
        path: dest_texture.to_string_lossy().to_string(),
        file_type: "texture".to_string(),
    });

    // Generate model/blockstate JSON and registration code
    let mut registration_code = None;

    match args.asset_type.as_str() {
        "block" => {
            // Block model JSON
            let models_dir = project
                .join("src/main/resources/assets")
                .join(&args.mod_id)
                .join("models/block");
            fs::create_dir_all(&models_dir)
                .map_err(|e| format!("Failed to create models directory: {}", e))?;

            let model_json = format!(
                r#"{{
  "parent": "minecraft:block/cube_all",
  "textures": {{
    "all": "{}:block/{}"
  }}
}}"#,
                args.mod_id, args.asset_name
            );
            let model_path = models_dir.join(format!("{}.json", args.asset_name));
            fs::write(&model_path, model_json)
                .map_err(|e| format!("Failed to write model: {}", e))?;
            created_files.push(CreatedFile {
                path: model_path.to_string_lossy().to_string(),
                file_type: "model".to_string(),
            });

            // Item model JSON (for block item)
            let item_models_dir = project
                .join("src/main/resources/assets")
                .join(&args.mod_id)
                .join("models/item");
            fs::create_dir_all(&item_models_dir)
                .map_err(|e| format!("Failed to create item models directory: {}", e))?;

            let item_model_json = format!(
                r#"{{
  "parent": "{}:block/{}"
}}"#,
                args.mod_id, args.asset_name
            );
            let item_model_path = item_models_dir.join(format!("{}.json", args.asset_name));
            fs::write(&item_model_path, item_model_json)
                .map_err(|e| format!("Failed to write item model: {}", e))?;
            created_files.push(CreatedFile {
                path: item_model_path.to_string_lossy().to_string(),
                file_type: "model".to_string(),
            });

            // Blockstate JSON
            let blockstates_dir = project
                .join("src/main/resources/assets")
                .join(&args.mod_id)
                .join("blockstates");
            fs::create_dir_all(&blockstates_dir)
                .map_err(|e| format!("Failed to create blockstates directory: {}", e))?;

            let blockstate_json = format!(
                r#"{{
  "variants": {{
    "": {{ "model": "{}:block/{}" }}
  }}
}}"#,
                args.mod_id, args.asset_name
            );
            let blockstate_path = blockstates_dir.join(format!("{}.json", args.asset_name));
            fs::write(&blockstate_path, blockstate_json)
                .map_err(|e| format!("Failed to write blockstate: {}", e))?;
            created_files.push(CreatedFile {
                path: blockstate_path.to_string_lossy().to_string(),
                file_type: "blockstate".to_string(),
            });

            if args.generate_code {
                let class_name = to_pascal_case(&args.asset_name);
                registration_code = Some(format!(
                    r#"// Register block: {}
public static final Block {upper} = Registry.register(
    Blocks.class,
    "{name}",
    new Block(BlockProperties.create()
        .hardness(3.0f)
        .resistance(3.0f))
);

// Register block item
public static final Item {upper}_ITEM = Registry.register(
    Items.class,
    "{name}",
    new BlockItem({upper}, new ItemProperties())
);"#,
                    class_name,
                    upper = args.asset_name.to_uppercase(),
                    name = args.asset_name
                ));
            }
        }
        "item" => {
            // Item model JSON
            let models_dir = project
                .join("src/main/resources/assets")
                .join(&args.mod_id)
                .join("models/item");
            fs::create_dir_all(&models_dir)
                .map_err(|e| format!("Failed to create models directory: {}", e))?;

            let model_json = format!(
                r#"{{
  "parent": "minecraft:item/generated",
  "textures": {{
    "layer0": "{}:item/{}"
  }}
}}"#,
                args.mod_id, args.asset_name
            );
            let model_path = models_dir.join(format!("{}.json", args.asset_name));
            fs::write(&model_path, model_json)
                .map_err(|e| format!("Failed to write model: {}", e))?;
            created_files.push(CreatedFile {
                path: model_path.to_string_lossy().to_string(),
                file_type: "model".to_string(),
            });

            if args.generate_code {
                registration_code = Some(format!(
                    r#"// Register item: {}
public static final Item {upper} = Registry.register(
    Items.class,
    "{name}",
    new Item(new ItemProperties()
        .maxStackSize(64))
);"#,
                    to_pascal_case(&args.asset_name),
                    upper = args.asset_name.to_uppercase(),
                    name = args.asset_name
                ));
            }
        }
        "gui_element" | "entity" | "particle" => {
            // These just copy the texture — no model/blockstate needed
            if args.generate_code {
                let note = match args.asset_type.as_str() {
                    "gui_element" => format!(
                        r#"// GUI texture: {name}
// Use in your screen class:
// private static final ResourceLocation TEXTURE = new ResourceLocation("{mod_id}", "textures/gui/{name}.png");"#,
                        mod_id = args.mod_id, name = args.asset_name
                    ),
                    "entity" => format!(
                        r#"// Entity texture: {name}
// Reference in your entity renderer:
// private static final ResourceLocation TEXTURE = new ResourceLocation("{mod_id}", "textures/entity/{name}.png");"#,
                        mod_id = args.mod_id, name = args.asset_name
                    ),
                    _ => format!(
                        r#"// Particle sprite: {name}
// Register in your particle factory:
// private static final ResourceLocation SPRITE = new ResourceLocation("{mod_id}", "textures/particle/{name}.png");"#,
                        mod_id = args.mod_id, name = args.asset_name
                    ),
                };
                registration_code = Some(note);
            }
        }
        _ => {}
    }

    Ok(ImportResult {
        created_files,
        registration_code,
        texture_path: dest_texture.to_string_lossy().to_string(),
    })
}

fn to_pascal_case(s: &str) -> String {
    s.split(|c: char| c == '-' || c == '_' || c == ' ')
        .filter(|w| !w.is_empty())
        .map(|word| {
            let mut chars = word.chars();
            match chars.next() {
                Some(c) => c.to_uppercase().to_string() + &chars.as_str().to_lowercase(),
                None => String::new(),
            }
        })
        .collect()
}

fn read_png_dimensions(data: &[u8]) -> Option<(u32, u32)> {
    // PNG header: 8 bytes magic, then IHDR chunk
    // IHDR starts at byte 8: 4 bytes length, 4 bytes "IHDR", then 4 bytes width, 4 bytes height
    if data.len() < 24 {
        return None;
    }
    // Check PNG magic
    if &data[0..4] != b"\x89PNG" {
        return None;
    }
    let width = u32::from_be_bytes([data[16], data[17], data[18], data[19]]);
    let height = u32::from_be_bytes([data[20], data[21], data[22], data[23]]);
    Some((width, height))
}
