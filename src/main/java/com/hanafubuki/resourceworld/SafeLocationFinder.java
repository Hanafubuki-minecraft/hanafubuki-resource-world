package com.hanafubuki.resourceworld;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/** Ищет случайную безопасную точку на поверхности, не блокируя сервер (чанки грузятся асинхронно). */
public final class SafeLocationFinder {

    private SafeLocationFinder() {
    }

    public static CompletableFuture<Location> find(World world, int minRadius, int maxRadius, int attempts) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        attempt(world, minRadius, maxRadius, attempts, result);
        return result;
    }

    private static void attempt(World world, int min, int max, int left, CompletableFuture<Location> result) {
        if (left <= 0) {
            result.complete(null);
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double angle = rnd.nextDouble(Math.PI * 2);
        double dist = min + rnd.nextDouble() * Math.max(1, max - min);
        Location center = world.getSpawnLocation();
        int x = (int) Math.round(center.getX() + Math.cos(angle) * dist);
        int z = (int) Math.round(center.getZ() + Math.sin(angle) * dist);

        world.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, err) -> {
            if (err != null || chunk == null) {
                attempt(world, min, max, left - 1, result);
                return;
            }
            Block ground = world.getHighestBlockAt(x, z);
            if (isSafeGround(ground) && world.getWorldBorder().isInside(ground.getLocation())) {
                result.complete(ground.getLocation().add(0.5, 1, 0.5));
            } else {
                attempt(world, min, max, left - 1, result);
            }
        });
    }

    private static boolean isSafeGround(Block ground) {
        Material type = ground.getType();
        if (!type.isSolid()) return false;
        if (Tag.LEAVES.isTagged(type)) return false;
        return switch (type) {
            case LAVA, WATER, MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, POWDER_SNOW, SWEET_BERRY_BUSH, POINTED_DRIPSTONE -> false;
            default -> ground.getRelative(0, 1, 0).isPassable() && ground.getRelative(0, 2, 0).isPassable();
        };
    }
}
