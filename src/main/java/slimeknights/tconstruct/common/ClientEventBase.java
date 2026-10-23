package slimeknights.tconstruct.common;

/**
 * Contains helpers to use for registering client events.
 *
 * <p>PORT 1.21.4: the {@code registerBlockItemColorAlias} helpers were removed. They depended on
 * {@code net.minecraft.client.color.item.ItemColors} and {@code RegisterColorHandlersEvent.Item}, both removed in 1.21.4.
 * Block-items that aliased their block color now express that tint through an {@code ItemTintSource} entry in the item
 * model JSON; re-add those per-item tints during Phase-4 runtime rewiring.
 */
public abstract class ClientEventBase {}
