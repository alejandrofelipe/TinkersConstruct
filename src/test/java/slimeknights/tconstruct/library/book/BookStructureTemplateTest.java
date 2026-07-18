package slimeknights.tconstruct.library.book;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.test.BaseMcTest;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity coverage for the book structure NBTs (foundry, smeltery). These are resolved by Mantle's
 * {@code BookRepository} (not the vanilla {@code StructureTemplateManager}), so they are unaffected by
 * the {@code structures/} → {@code structure/} directory rename that broke the islands — this test is the
 * regression guard proving that separation holds. It also reproduces {@code ContentStructure}'s reflective
 * read of the private {@code StructureTemplate.palettes} field (needed because 1.21.1 removed the public
 * accessor) so the book renders a non-empty structure.
 */
class BookStructureTemplateTest extends BaseMcTest {
  private static final List<String> BOOK_STRUCTURES = List.of("foundry", "smeltery");

  @Test
  void bookStructureNbtsLoadWithPaletteBlocks() throws Exception {
    for (String name : BOOK_STRUCTURES) {
      String path = "assets/tconstruct/book/structures/" + name + ".nbt";
      URL resource = getClass().getClassLoader().getResource(path);
      assertThat(resource).as("book structure NBT should exist: %s", path).isNotNull();

      StructureTemplate template = new StructureTemplate();
      try (InputStream in = resource.openStream()) {
        CompoundTag nbt = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        template.load(BuiltInRegistries.BLOCK.asLookup(), nbt);
      }

      var size = template.getSize();
      assertThat(size.getX() * size.getY() * size.getZ())
        .as("%s book structure should have a non-empty volume", name)
        .isGreaterThan(0);

      // reproduce Mantle ContentStructure.readFirstPaletteBlocks to prove the reflective palette read still resolves
      assertThat(reflectFirstPaletteBlocks(template))
        .as("%s book structure should expose palette blocks via reflection (ContentStructure)", name)
        .isNotEmpty();
    }
  }

  /** Mirrors {@code ContentStructure.readFirstPaletteBlocks}: reflectively read the first palette's block list. */
  private static List<?> reflectFirstPaletteBlocks(StructureTemplate template) throws Exception {
    Field palettesField = StructureTemplate.class.getDeclaredField("palettes");
    palettesField.setAccessible(true);
    List<?> palettes = (List<?>) palettesField.get(template);
    assertThat(palettes).as("StructureTemplate.palettes should not be empty").isNotEmpty();
    Object palette = palettes.get(0);
    return (List<?>) palette.getClass().getMethod("blocks").invoke(palette);
  }
}
