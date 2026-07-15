package slimeknights.tconstruct.library.modifiers.modules.capacity;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.modifiers.hook.special.CapacityBarHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.test.BaseMcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests {@link CapacityBarValidator}: display-name suffixing, removal cleanup, and {@code validate}.
 *
 * <p><b>Latent bug found and fixed:</b> {@code validate()} computed {@code cap} via
 * {@code bar.getCapacity(tool, modifier)} and then re-checked {@code bar.getCapacity(tool, modifier) > cap} -
 * comparing that same call against itself, which can never be true, so the "clear excess amount" cleanup the
 * method's own comment describes was dead code. Fixed to compare the current amount instead:
 * {@code bar.getAmount(tool) > cap}. See {@link #validate_amountAboveCapacity_clampsDownToCapacity()}.
 */
class CapacityBarValidatorTest extends BaseMcTest {
  @BeforeAll
  static void setup() {
    ModifierFixture.init();
  }

  @Test
  void getDisplayName_appendsAmountOverCapacity() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry entry = new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 2);
    when(bar.getAmount(tool)).thenReturn(7);
    when(bar.getCapacity(tool, entry)).thenReturn(20);

    CapacityBarValidator validator = new CapacityBarValidator(bar);
    // the base name comes from entry.getModifier().getDisplayName(level), not the passed-in "name" argument -
    // only the "amount / capacity" suffix this method itself contributes is asserted here
    Component name = validator.getDisplayName(tool, entry, Component.literal("ignored"), null);
    assertThat(name.getString()).endsWith(": 7 / 20");
  }

  @Test
  void validate_amountWithinCapacity_neverTouchesBar() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry entry = new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1);
    when(bar.getCapacity(tool, entry)).thenReturn(50);

    CapacityBarValidator validator = new CapacityBarValidator(bar);
    assertThat(validator.validate(tool, entry)).isNull();
    verify(bar, never()).setAmount(any(IToolStackView.class), any(ModifierEntry.class), anyInt());
  }

  @Test
  void validate_amountAboveCapacity_clampsDownToCapacity() {
    // see class javadoc: validate() now compares bar.getAmount(tool) against cap, not cap against itself
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    ModifierEntry entry = new ModifierEntry(ModifierFixture.TEST_MODIFIER_1, 1);
    when(bar.getCapacity(tool, entry)).thenReturn(50);
    when(bar.getAmount(tool)).thenReturn(999); // way above capacity; the fixed guard clamps this down

    CapacityBarValidator validator = new CapacityBarValidator(bar);
    assertThat(validator.validate(tool, entry)).isNull();
    verify(bar).setAmount(tool, entry, 50);
  }

  @Test
  void onRemoved_resetsBarToZeroViaEmptyEntry() {
    CapacityBarHook bar = mock(CapacityBarHook.class);
    IToolStackView tool = mock(IToolStackView.class);
    CapacityBarValidator validator = new CapacityBarValidator(bar);
    Component result = validator.onRemoved(tool, ModifierFixture.TEST_MODIFIER_1);
    assertThat(result).isNull();
    verify(bar).setAmount(tool, ModifierEntry.EMPTY, 0);
  }

  @Test
  void getDefaultHooks_hasDisplayValidateAndRemoveHooks() {
    CapacityBarValidator validator = new CapacityBarValidator(mock(CapacityBarHook.class));
    assertThat(validator.getDefaultHooks()).hasSize(3);
  }
}
