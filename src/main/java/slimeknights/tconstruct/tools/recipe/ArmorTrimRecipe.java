package slimeknights.tconstruct.tools.recipe;

import lombok.Getter;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.component.ProvidesTrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import slimeknights.mantle.recipe.IMultiRecipe;
import slimeknights.mantle.util.RegistryHelper;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.common.recipe.ILookupRegistrar;
import slimeknights.tconstruct.library.json.IntRange;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.modifiers.ModifierRecipeLookup;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IDisplayModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationRecipe;
import slimeknights.tconstruct.library.tools.item.IModifiableDisplay;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.TinkerModifiers;
import slimeknights.tconstruct.tools.modules.cosmetic.TrimModule;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ArmorTrimRecipe implements ITinkerStationRecipe, IMultiRecipe<IDisplayModifierRecipe>, ILookupRegistrar {
  protected static final String KEY_INVALID_MATERIAL = TConstruct.makeTranslationKey("recipe", "modifier.armor_trim.invalid_material");
  protected static final String KEY_INVALID_PATTERN = TConstruct.makeTranslationKey("recipe", "modifier.armor_trim.invalid_pattern");


  /** PORT M3 (recipes): 1.21 recipes no longer carry an id; retained for JEI display only. */
  @Getter
  private final ResourceLocation id = TConstruct.getResource("armor_trim_modifier");

  public ArmorTrimRecipe() {}

  @Override
  public void registerLookups() {
    ModifierRecipeLookup.addRecipeModifier(null, TinkerModifiers.trim);
  }

  /** Suffix vanilla uses for armor trim template items */
  private static final String TEMPLATE_SUFFIX = "_armor_trim_smithing_template";

  /**
   * Finds the trim pattern for the given template.
   * PORT 1.21.5: templates no longer know their pattern (the smithing trim recipes do, but those are not available on the client),
   * so this matches the vanilla template naming convention {@code <pattern>_armor_trim_smithing_template}.
   */
  private static Optional<Reference<TrimPattern>> getPattern(HolderLookup.Provider access, ItemStack template) {
    ResourceLocation id = BuiltInRegistries.ITEM.getKey(template.getItem());
    String path = id.getPath();
    if (path.endsWith(TEMPLATE_SUFFIX)) {
      return access.lookupOrThrow(Registries.TRIM_PATTERN).get(ResourceKey.create(Registries.TRIM_PATTERN, id.withPath(path.substring(0, path.length() - TEMPLATE_SUFFIX.length()))));
    }
    return Optional.empty();
  }

  /** Match for the trim item finding method */
  private record TrimItems(ItemStack template, ItemStack material) {}

  /** Finds the trim template and material */
  @Nullable
  private static TrimItems findInputs(ITinkerStationContainer inv) {
    ItemStack template = ItemStack.EMPTY;
    ItemStack material = ItemStack.EMPTY;
    for (int i = 0; i < inv.getInputCount(); i++) {
      ItemStack stack = inv.getInput(i);
      if (!stack.isEmpty()) {
        // find the two matching tags, but ensure no duplicates
        if (stack.getItem() instanceof SmithingTemplateItem) {
          if (!template.isEmpty()) {
            return null;
          }
          template = stack;
        }
        if (stack.is(ItemTags.TRIM_MATERIALS)) {
          if (!material.isEmpty()) {
            return null;
          }
          material = stack;
        }
      }
    }
    // if we found both, we match
    if (!material.isEmpty() && !template.isEmpty()) {
      return new TrimItems(template, material);
    }
    return null;
  }

  @Override
  public boolean matches(ITinkerStationContainer inv, Level world) {
    // ensure this modifier can be applied
    if (!inv.getTinkerableStack().is(TinkerTags.Items.TRIM)) {
      return false;
    }
    // need to locate two things: the trim material, and the trim template
    return findInputs(inv) != null;
  }

  @Override
  public RecipeResult<LazyToolStack> getValidatedResult(ITinkerStationContainer inv, HolderLookup.Provider access) {
    // first need to find our trim and material instances
    TrimItems trimItems = findInputs(inv);
    // should never happen
    if (trimItems == null) {
      return RecipeResult.pass();
    }
    // validate the material nad pattern items
    // 1.21.5: the material comes from the ingredient's provides trim material component, and may be a direct holder
    ResourceKey<TrimMaterial> material = TrimMaterials.getFromIngredient(access, trimItems.material).flatMap(Holder::unwrapKey).orElse(null);
    if (material == null) {
      return RecipeResult.failure(KEY_INVALID_MATERIAL, trimItems.material.getDisplayName());
    }
    ToolStack original = inv.getTinkerable();
    Reference<TrimPattern> pattern = null;
    if (!original.hasTag(TinkerTags.Items.TRIM_NO_PATTERN)) {
      pattern = getPattern(access, trimItems.template).orElse(null);
      if (pattern == null) {
        return RecipeResult.failure(KEY_INVALID_PATTERN, trimItems.template.getDisplayName());
      }
    }

    // store into tool NBT
    ToolStack tool = inv.getTinkerable().copy();
    ModDataNBT persistentData = tool.getPersistentData();
    ModifierId modifier = TinkerModifiers.trim.getId();
    persistentData.putString(TrimModule.materialKey(modifier), material.location().toString());
    if (pattern != null) {
      persistentData.putString(TrimModule.patternKey(modifier), pattern.key().location().toString());
    }

    // add the modifier if missing
    if (tool.getModifierLevel(modifier) == 0) {
      tool.addModifier(modifier, 1);
    }
    return ITinkerStationRecipe.success(tool, inv);
  }

  @Override
  public RecipeSerializer<? extends Recipe<ITinkerStationContainer>> getSerializer() {
    return TinkerModifiers.armorTrimSerializer.get();
  }


  /* JEI */

  private List<IDisplayModifierRecipe> displayRecipes = null;

  @SuppressWarnings("deprecation")
  @Override
  public List<IDisplayModifierRecipe> getRecipes(RegistryAccess access) {
    if (displayRecipes == null) {
      // 1.21.5: patterns no longer know their template item, see getPattern
      List<ItemStack> trims = access.lookupOrThrow(Registries.TRIM_PATTERN).listElements()
                                            .flatMap(pattern -> BuiltInRegistries.ITEM.getOptional(pattern.key().location().withSuffix(TEMPLATE_SUFFIX)).stream())
                                            .map(ItemStack::new).toList();
      List<ItemStack> toolInputs = RegistryHelper.getTagValueStream(BuiltInRegistries.ITEM, TinkerTags.Items.TRIM)
                                                 .map(IModifiableDisplay::getDisplayStack).toList();
      if (!trims.isEmpty() && !toolInputs.isEmpty()) {
        ResourceLocation id = getId();
        displayRecipes = access.lookupOrThrow(Registries.TRIM_MATERIAL).listElements()
          .map(material -> new DisplayRecipe(id, toolInputs, trims, material, access))
          .collect(Collectors.toList());
      } else {
        displayRecipes = List.of();
      }
    }
    return displayRecipes;
  }

  private static class DisplayRecipe implements IDisplayModifierRecipe {
    private static final IntRange LEVELS = new IntRange(1, 1);
    private final ModifierEntry RESULT = new ModifierEntry(TinkerModifiers.trim, 1);

    @Getter
    private final ResourceLocation recipeId;
    @Getter
    private final List<ItemStack> toolWithoutModifier;
    @Getter
    private final List<ItemStack> toolWithModifier;
    private final List<ItemStack> trim;
    private final List<ItemStack> material;
    @Getter
    private final Component variant;

    public DisplayRecipe(ResourceLocation id, List<ItemStack> tools, List<ItemStack> trim, Reference<TrimMaterial> holder, HolderLookup.Provider access) {
      this.recipeId = id;
      TrimMaterial material = holder.value();
      toolWithoutModifier = tools;
      this.trim = trim;
      // 1.21.5: trim materials no longer store their ingredient, find the items providing this material
      ResourceKey<TrimMaterial> materialKey = holder.key();
      this.material = BuiltInRegistries.ITEM.stream().filter(item -> {
        ProvidesTrimMaterial provides = item.components().get(DataComponents.PROVIDES_TRIM_MATERIAL);
        return provides != null && provides.unwrap(access).flatMap(Holder::unwrapKey).filter(materialKey::equals).isPresent();
      }).map(ItemStack::new).toList();
      this.variant = material.description().plainCopy();

      String materialName = holder.key().location().toString();
      List<ModifierEntry> results = List.of(RESULT);
      ResourceLocation key = TrimModule.materialKey(TinkerModifiers.trim.getId());
      toolWithModifier = tools.stream().map(stack -> IDisplayModifierRecipe.withModifiers(stack, results, data -> data.putString(key, materialName))).toList();

    }

    @Override
    public int getInputCount() {
      return 2;
    }

    @Override
    public List<ItemStack> getDisplayItems(int slot) {
      return switch (slot) {
        case 0 -> trim;
        case 1 -> material;
        default -> List.of();
      };
    }

    @Override
    public ModifierEntry getDisplayResult() {
      return RESULT;
    }

    @Override
    public IntRange getLevel() {
      return LEVELS;
    }
  }
}
