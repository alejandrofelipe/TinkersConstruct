package slimeknights.tconstruct.world.block;

/**
 * Marker interface for blocks that count as slime plants.
 *
 * <p>Replaces the old Forge {@code PlantType}/{@code IPlantable} mechanism (removed in NeoForge
 * 1.21.1): slime soil blocks check {@code instanceof SlimePlant} in their
 * {@link net.neoforged.neoforge.common.extensions.IBlockExtension#canSustainPlant} override instead
 * of comparing a {@code PlantType}.
 */
public interface SlimePlant {}
