package slimeknights.tconstruct.library.modifiers.modules.behavior;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import slimeknights.mantle.data.loadable.common.ItemStackLoadable;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.modules.ModifierModule;
import slimeknights.tconstruct.library.modifiers.modules.util.ModifierCondition;
import slimeknights.tconstruct.library.module.ModuleHook;
import slimeknights.tconstruct.library.tools.capability.BlockItemProviderModifierHook;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * A module that uses {@link slimeknights.tconstruct.library.tools.capability.BlockItemProviderCapability BlockItemProviderCapability} via {@link BlockItemProviderModifierHook} to provide BlockItems to modifiers like exchanging at the cost of durability.
 * Note this does not let the tool place blocks, it only exposes this capability. See {@link slimeknights.tconstruct.tools.modules.interaction.PlaceGlowModule PlaceGlowModule} for an example of a custom module that lets the tool place blocks.
 * 26.1: no longer a record. The item is a template (modifiers decode before item components are bound), and the provided
 * stack is built on first use and reused, as {@link #consumeBlockItem} identifies its own stack by instance. Modules are
 * decoded again on every reload, so the stack never outlives the components it was built with.
 * @param item The BlockItem to provide
 * @param damage The amount of damage it takes to provide one block (can be 0)
 * @param condition Other conditions that you might want to condition the providing on, such as only happening on certain tool types.
 */
@Accessors(fluent = true)
@Getter
@EqualsAndHashCode(exclude = "stack")
@ToString(exclude = "stack")
@RequiredArgsConstructor
public final class BlockItemProviderModule implements ModifierModule, BlockItemProviderModifierHook, ModifierCondition.ConditionalModule<IToolStackView> {
    private static final List<ModuleHook<?>> DEFAULT_HOOKS = List.of(ModifierHooks.BLOCK_ITEM_PROVIDER);
    public static final RecordLoadable<BlockItemProviderModule> LOADER = RecordLoadable.create(
      ItemStackLoadable.REQUIRED_ITEM_NBT_TEMPLATE.validate((template, error) -> {
        Item item = template.item().value();
        if (item instanceof BlockItem) {
          return template;
        }
        throw error.create(String.format("Expected item %s to be instance of BlockItem, but was %s instead", BuiltInRegistries.ITEM.getKey(item), item.getClass().getName()));
      }).requiredField("item", BlockItemProviderModule::item),
      IntLoadable.FROM_ZERO.defaultField("tool_damage", 1, BlockItemProviderModule::damage),
      ModifierCondition.TOOL_FIELD,
      BlockItemProviderModule::new);

    private final ItemStackTemplate item;
    private final int damage;
    private final ModifierCondition<IToolStackView> condition;
    /** Provided stack, built on first use */
    @Getter(AccessLevel.NONE)
    @Nullable
    private ItemStack stack;

    /** Gets the provided stack, building it on first use */
    private ItemStack stack() {
        if (stack == null) {
            stack = item.create();
        }
        return stack;
    }

    @Override
    public RecordLoadable<BlockItemProviderModule> getLoader() {
        return LOADER;
    }

    @Override
    public List<ModuleHook<?>> getDefaultHooks() {
        return DEFAULT_HOOKS;
    }

    @Override
    public ItemStack getBlockItemStack(IToolStackView tool, ModifierEntry modifier, @Nullable LivingEntity entity) {
        return !tool.isBroken() && condition.matches(tool, modifier) ? stack() : ItemStack.EMPTY;
    }

    @Override
    public boolean consumeBlockItem(IToolStackView tool, ModifierEntry modifier, ItemStack backingStack, @Nullable LivingEntity entity) {
        // if this is not our item, then we did not provide it so we should avoid consuming
        if (stack == null || stack != backingStack) return false;

        // we did provide it, so damage and show animation if possible
        if (damage > 0) {
            ToolDamageUtil.damageAnimated(tool, damage, entity, modifier.getId());
        }
        return true;
    }
}
