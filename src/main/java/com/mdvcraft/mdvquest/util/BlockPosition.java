package com.mdvcraft.mdvquest.util;

import org.bukkit.block.Block;

import java.util.UUID;

public record BlockPosition(UUID world, int x, int y, int z) {
    public static BlockPosition of(Block block) {
        return new BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
    }
}
