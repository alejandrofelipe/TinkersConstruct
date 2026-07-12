package slimeknights.tconstruct.test;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.ToolDefinitionData;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.nbt.DummyToolStack;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ModifierNBT;
import slimeknights.tconstruct.library.tools.nbt.MultiplierNBT;
import slimeknights.tconstruct.library.tools.nbt.StatsNBT;
import slimeknights.tconstruct.library.tools.stat.INumericToolStat;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;

import javax.annotation.Nullable;
import java.util.List;

/** Helpers for running tests */
public class TestHelper {
  private TestHelper() {}

  /**
   * PORT M6: test-only equivalent of the old {@code ItemStack#getTag()}. 1.21 stacks no longer carry a
   * free-form tag; the closest equivalent is the stack's {@link DataComponents#CUSTOM_DATA} component.
   * Mirrors the private helper {@code ToolStack#getStackTag}.
   * @return  The live mutable tag backing the component, or null if the component is absent.
   */
  @Nullable
  public static CompoundTag getTag(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    return data == null ? null : data.getUnsafe();
  }

  /**
   * PORT M6: test-only equivalent of the old {@code ItemStack#getOrCreateTag()}; attaches an empty
   * custom-data component if the stack doesn't have one yet. Mirrors the private helper pair
   * {@code ToolStack#getStackTag}/{@code ToolStack#setStackTag}.
   */
  public static CompoundTag getOrCreateTag(ItemStack stack) {
    CompoundTag tag = getTag(stack);
    if (tag == null) {
      tag = new CompoundTag();
      // CustomData.of(tag) copies the tag, so fetch the actually-stored instance back out
      stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
      tag = getTag(stack);
    }
    return tag;
  }

  /** Helper to fetch traits from the trait hook */
  public static List<ModifierEntry> getTraits(ToolDefinitionData data) {
    ModifierNBT.Builder builder = ModifierNBT.builder();
    data.getHook(ToolHooks.TOOL_TRAITS).addTraits(ToolDefinition.EMPTY, MaterialNBT.EMPTY, builder);
    return builder.build().getModifiers();
  }

  public record ToolDefinitionStats(StatsNBT base, MultiplierNBT multipliers) {}

  /** Computes the stats for the given tool */
  public static ToolDefinitionStats buildStats(ToolDefinitionData data) {
    ModifierStatsBuilder builder = ModifierStatsBuilder.builder();
    data.getHook(ToolHooks.TOOL_STATS).addToolStats(new DummyToolStack(Items.AIR, ModifierNBT.EMPTY, new ModDataNBT()), builder);
    MultiplierNBT multipliers = builder.buildMultipliers();
    // cancel out multipliers on the base stats, as people expect base stats to be comparable to be usable in the modifier stats builder
    for (INumericToolStat<?> stat : multipliers.getContainedStats()) {
      stat.multiply(builder, 1 / multipliers.get(stat));
    }
    return new ToolDefinitionStats(builder.build(), multipliers);
  }
}
