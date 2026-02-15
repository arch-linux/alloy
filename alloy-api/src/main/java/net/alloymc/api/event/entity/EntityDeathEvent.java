package net.alloymc.api.event.entity;

import net.alloymc.api.entity.LivingEntity;
import net.alloymc.api.inventory.ItemStack;

import java.util.List;

/**
 * Fired when a living entity dies.
 */
public class EntityDeathEvent extends EntityEvent {

    private final List<ItemStack> drops;
    private int droppedExp;

    public EntityDeathEvent(LivingEntity entity, List<ItemStack> drops, int droppedExp) {
        super(entity);
        this.drops = drops;
        this.droppedExp = droppedExp;
    }

    @Override
    public LivingEntity entity() {
        return (LivingEntity) super.entity();
    }

    /**
     * Mutable list of items dropped. Modify to change drops.
     */
    public List<ItemStack> drops() { return drops; }

    public int droppedExp() { return droppedExp; }
    public void setDroppedExp(int exp) { this.droppedExp = exp; }
}
