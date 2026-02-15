package net.alloymc.mod.griefprevention.visualization;

import net.alloymc.api.inventory.Material;

/**
 * Types of claim boundary visualizations.
 */
public enum VisualizationType {

    /**
     * Normal player claim — shown with glowstone corners and gold blocks on edges.
     */
    CLAIM(Material.GLOWSTONE, Material.GOLD_BLOCK),

    /**
     * Admin claim — shown with glowstone corners and pumpkins on edges.
     */
    ADMIN_CLAIM(Material.GLOWSTONE, Material.PUMPKIN),

    /**
     * Subdivision within a claim — shown with iron blocks and wool.
     */
    SUBDIVISION(Material.IRON_BLOCK, Material.DIAMOND_BLOCK),

    /**
     * Conflict / overlap indicator — shown with redstone blocks and netherrack.
     */
    CONFLICT(Material.GLOWSTONE, Material.NETHERRACK);

    private final Material cornerMaterial;
    private final Material edgeMaterial;

    VisualizationType(Material cornerMaterial, Material edgeMaterial) {
        this.cornerMaterial = cornerMaterial;
        this.edgeMaterial = edgeMaterial;
    }

    public Material cornerMaterial() { return cornerMaterial; }
    public Material edgeMaterial() { return edgeMaterial; }
}
