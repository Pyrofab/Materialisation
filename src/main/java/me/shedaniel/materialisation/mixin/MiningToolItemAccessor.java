package me.shedaniel.materialisation.mixin;

import net.minecraft.block.Block;
import net.minecraft.item.MiningToolItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(MiningToolItem.class)
public interface MiningToolItemAccessor {

    @Accessor("effectiveBlocks")
    Set<Block> getEffectiveBlocks();

}
