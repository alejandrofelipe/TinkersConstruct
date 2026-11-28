package slimeknights.mantle.client.screen.book;

import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Book text colors by name: parity with the {@code ChatFormatting#getByName} that MC 26.2 removed */
class TextDataRendererTest {
  @Test
  void formattingByName_ignoresCaseAndNonLetters() {
    assertThat(TextDataRenderer.formattingByName("dark_blue")).isEqualTo(ChatFormatting.DARK_BLUE);
    assertThat(TextDataRenderer.formattingByName("DarkBlue")).isEqualTo(ChatFormatting.DARK_BLUE);
    assertThat(TextDataRenderer.formattingByName("darkblue")).isEqualTo(ChatFormatting.DARK_BLUE);
    assertThat(TextDataRenderer.formattingByName("GOLD")).isEqualTo(ChatFormatting.GOLD);
  }

  @Test
  void formattingByName_findsFormatsToo() {
    assertThat(TextDataRenderer.formattingByName("bold")).isEqualTo(ChatFormatting.BOLD);
    assertThat(TextDataRenderer.formattingByName("reset")).isEqualTo(ChatFormatting.RESET);
  }

  @Test
  void formattingByName_unknownOrNull() {
    assertThat(TextDataRenderer.formattingByName("not_a_color")).isNull();
    assertThat(TextDataRenderer.formattingByName(null)).isNull();
  }
}
