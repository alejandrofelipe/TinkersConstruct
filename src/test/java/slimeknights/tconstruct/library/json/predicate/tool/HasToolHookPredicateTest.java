package slimeknights.tconstruct.library.json.predicate.tool;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ItemAbilities;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.ToolDefinitionDataBuilder;
import slimeknights.tconstruct.library.tools.definition.module.ToolHooks;
import slimeknights.tconstruct.library.tools.definition.module.build.ToolActionsModule;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Tests {@link HasToolHookPredicate}: checks whether a definition has a module contributing the given hook. */
class HasToolHookPredicateTest extends BaseMcTest {
  private static IToolContext toolWithDefinition(ToolDefinition definition) {
    IToolContext tool = mock(IToolContext.class);
    when(tool.getDefinition()).thenReturn(definition);
    return tool;
  }

  @Test
  void matches_falseWhenNoModuleContributesTheHook() {
    HasToolHookPredicate predicate = new HasToolHookPredicate(ToolHooks.TOOL_ACTION);
    assertThat(predicate.matches(toolWithDefinition(ToolDefinition.EMPTY))).isFalse();
  }

  @Test
  void matches_trueWhenAModuleContributesTheHook() {
    ToolDefinition definition = new ToolDefinition(ResourceLocation.fromNamespaceAndPath("test", "hook_test"));
    definition.setData(ToolDefinitionDataBuilder.builder().module(ToolActionsModule.of(ItemAbilities.SHOVEL_FLATTEN)).build());

    HasToolHookPredicate predicate = new HasToolHookPredicate(ToolHooks.TOOL_ACTION);
    assertThat(predicate.matches(toolWithDefinition(definition))).isTrue();
  }

  @Test
  void matches_falseForAnUnrelatedHook() {
    ToolDefinition definition = new ToolDefinition(ResourceLocation.fromNamespaceAndPath("test", "hook_test_2"));
    definition.setData(ToolDefinitionDataBuilder.builder().module(ToolActionsModule.of(ItemAbilities.SHOVEL_FLATTEN)).build());

    HasToolHookPredicate predicate = new HasToolHookPredicate(ToolHooks.VOLATILE_DATA);
    assertThat(predicate.matches(toolWithDefinition(definition))).isFalse();
  }

  @Test
  void loader_serializeThenDeserialize_roundTrips() {
    HasToolHookPredicate original = new HasToolHookPredicate(ToolHooks.TOOL_ACTION);
    JsonObject json = new JsonObject();
    HasToolHookPredicate.LOADER.serialize(original, json);
    HasToolHookPredicate roundTripped = HasToolHookPredicate.LOADER.deserialize(json);
    assertThat(roundTripped.hook()).isEqualTo(ToolHooks.TOOL_ACTION);
  }
}
