package slimeknights.tconstruct.library.recipe.ingredient;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Helper producing a vanilla {@link Ingredient} backed by an item tag that resolves lazily.
 *
 * <p>1.21.4 removed the lazy {@code Ingredient.of(TagKey)} overload; the remaining
 * {@code Ingredient.of(HolderSet)} needs a resolved {@link HolderSet}. Resolving
 * {@code BuiltInRegistries.ITEM.getOrThrow(tag)} eagerly throws "Tags not bound" when it runs in a static field or any
 * registration/datagen context (the static registry binds tags only when a world or datapack loads).
 *
 * <p>This {@link HolderSet} reports itself as the tag via {@link #unwrap()} (so the ingredient serializes as
 * {@code "#tag"}, exactly like the old behavior) and defers {@link #stream()}/{@link #size()} to the real tag holder set,
 * resolved on first use. Membership ({@link #contains(Holder)}) goes through {@link Holder#is(TagKey)}, which needs no
 * resolution. The result: no crash at class-load, identical {@code #tag} datagen output, correct runtime matching.
 */
public final class LazyTagIngredient implements HolderSet<Item> {
  private final TagKey<Item> tag;
  @Nullable
  private HolderSet.Named<Item> resolved;

  private LazyTagIngredient(TagKey<Item> tag) {
    this.tag = tag;
  }

  /** Creates a vanilla ingredient matching the items in the given tag, resolving the tag lazily */
  public static Ingredient of(TagKey<Item> tag) {
    return Ingredient.of(new LazyTagIngredient(tag));
  }

  /** Resolves the backing tag holder set; only call once tags have bound (runtime), never at load/datagen */
  private HolderSet.Named<Item> resolve() {
    HolderSet.Named<Item> set = this.resolved;
    if (set == null) {
      set = BuiltInRegistries.ITEM.getOrThrow(this.tag);
      this.resolved = set;
    }
    return set;
  }

  @Override
  public Either<TagKey<Item>,List<Holder<Item>>> unwrap() {
    return Either.left(this.tag);
  }

  @Override
  public Optional<TagKey<Item>> unwrapKey() {
    return Optional.of(this.tag);
  }

  @Override
  public boolean contains(Holder<Item> holder) {
    // tag membership resolves through the holder, so this works without binding the set
    return holder.is(this.tag);
  }

  @Override
  public boolean canSerializeIn(HolderOwner<Item> owner) {
    // item tags are always valid in the item registry context used by recipe serialization
    return true;
  }

  @Override
  public boolean isBound() {
    try {
      return resolve().isBound();
    } catch (RuntimeException e) {
      return false;
    }
  }

  @Override
  public Stream<Holder<Item>> stream() {
    return resolve().stream();
  }

  @Override
  public int size() {
    return resolve().size();
  }

  @Override
  public Holder<Item> get(int index) {
    return resolve().get(index);
  }

  @Override
  public Optional<Holder<Item>> getRandomElement(RandomSource random) {
    return resolve().getRandomElement(random);
  }

  @Override
  public Iterator<Holder<Item>> iterator() {
    return resolve().iterator();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof LazyTagIngredient lazy && this.tag.equals(lazy.tag);
  }

  @Override
  public int hashCode() {
    return this.tag.hashCode();
  }

  @Override
  public String toString() {
    return "LazyTagIngredient(" + this.tag.location() + ")";
  }
}
