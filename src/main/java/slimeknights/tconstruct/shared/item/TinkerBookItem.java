package slimeknights.tconstruct.shared.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
// PORT book: Mantle's book system (AbstractBookItem / BookScreenOpener) and TinkerBook are not yet ported (M5+).
//import slimeknights.mantle.client.book.BookScreenOpener;
//import slimeknights.mantle.item.AbstractBookItem;
//import slimeknights.tconstruct.library.client.book.TinkerBook;

public class TinkerBookItem extends Item {
  private final BookType bookType;
  public TinkerBookItem(Properties props, BookType bookType) {
    super(props);
    this.bookType = bookType;
  }

  // PORT book: book-opening stubbed until Mantle's book system is ported. Keep the item registerable;
  // opening is a no-op for now (was: open TinkerBook.getBook(bookType) via AbstractBookItem#use).
  @Override
  public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    return InteractionResultHolder.pass(player.getItemInHand(hand));
  }

  // PORT book: restore once Mantle's BookScreenOpener is ported.
  //@Override
  //public BookScreenOpener getBook(ItemStack stack) {
  //  return TinkerBook.getBook(bookType);
  //}

  /** Simple enum to allow selecting the book on the client */
  public enum BookType {
    MATERIALS_AND_YOU,
    PUNY_SMELTING,
    MIGHTY_SMELTING,
    TINKERS_GADGETRY,
    FANTASTIC_FOUNDRY,
    ENCYCLOPEDIA
  }
}
