package slimeknights.tconstruct.library.modifiers.modules.capacity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.special.CapacityBarHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.test.BaseMcTest;

import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests {@link CapacitySourceModule}: the shared "owner" indirection (a module may report its capacity bar amount
 * onto a different modifier than itself) and the static {@link CapacitySourceModule#apply} grant dispatcher used by
 * every capacity-granting module in this package.
 */
class CapacitySourceModuleTest extends BaseMcTest {
  @BeforeAll
  static void setup() {
    ModifierFixture.init();
  }

  private static final ModifierEntry ENTRY = new ModifierEntry(new ModifierId("test", "foo"), 3);
  private static final ModifierId OWNER_ID = new ModifierId("test", "owner");

  /** Minimal implementation, only {@link CapacitySourceModule#owner()} is needed to exercise the default methods. */
  private record TestSource(@Nullable ModifierId owner) implements CapacitySourceModule {}

  /** Binds the given bar as the {@link ModifierHooks#CAPACITY_BAR} hook of a freshly composed, ID-bound {@link Modifier}. */
  private static ModifierEntry entryWithBar(CapacityBarHook bar, int level) {
    Modifier modifier = ModifierFixture.withHooks(ModuleHookMap.builder().addHook(bar, ModifierHooks.CAPACITY_BAR).build());
    return new ModifierEntry(modifier, level);
  }

  @Test
  void barModifier_noOwner_returnsEntryItself() {
    TestSource source = new TestSource(null);
    IToolStackView tool = mock(IToolStackView.class);
    assertThat(source.barModifier(tool, ENTRY)).isSameAs(ENTRY);
  }

  @Test
  void barModifier_withOwner_delegatesToToolGetModifier() {
    TestSource source = new TestSource(OWNER_ID);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry ownerEntry = new ModifierEntry(OWNER_ID, 7);
    when(tool.getModifier(OWNER_ID)).thenReturn(ownerEntry);
    assertThat(source.barModifier(tool, ENTRY)).isSameAs(ownerEntry);
  }

  @Test
  void apply_zeroGrant_resetsAmountToZero() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry entry = entryWithBar(bar, 1);
    IToolStackView tool = mock(IToolStackView.class);
    CapacitySourceModule.apply(tool, entry, 10, 0);
    verify(bar).setAmount(tool, entry, 0);
    verifyNoMoreInteractions(bar);
  }

  @Test
  void apply_positiveGrant_addsAmountTimesGrant() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry entry = entryWithBar(bar, 1);
    IToolStackView tool = mock(IToolStackView.class);
    CapacitySourceModule.apply(tool, entry, 10, 3);
    verify(bar).addAmount(tool, entry, 30);
    verifyNoMoreInteractions(bar);
  }

  @Test
  void apply_negativeGrant_removesAmountTimesAbsoluteGrant() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry entry = entryWithBar(bar, 1);
    IToolStackView tool = mock(IToolStackView.class);
    CapacitySourceModule.apply(tool, entry, 10, -2);
    verify(bar).removeAmount(tool, entry, 20);
    verifyNoMoreInteractions(bar);
  }

  @Test
  void apply_usesOwnerIndirectionForBarLookup() {
    // the bar hook must be fetched off the *owner* modifier, not the granting one
    CapacityBarHook bar = mock(CapacityBarHook.class);
    ModifierEntry ownerEntry = entryWithBar(bar, 1);
    IToolStackView tool = mock(IToolStackView.class);
    when(tool.getModifier(OWNER_ID)).thenReturn(ownerEntry);

    TestSource source = new TestSource(OWNER_ID);
    ModifierEntry grantingEntry = source.barModifier(tool, ENTRY);
    CapacitySourceModule.apply(tool, grantingEntry, 5, 1);
    verify(bar).addAmount(tool, ownerEntry, 5);
  }
}
