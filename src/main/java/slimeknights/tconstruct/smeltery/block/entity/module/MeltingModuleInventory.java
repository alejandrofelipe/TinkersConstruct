package slimeknights.tconstruct.smeltery.block.entity.module;

import net.minecraft.nbt.ByteTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import slimeknights.mantle.block.entity.MantleBlockEntity;
import slimeknights.tconstruct.library.recipe.melting.IMeltingContainer.IOreRate;
import slimeknights.tconstruct.library.recipe.melting.IMeltingRecipe;
import slimeknights.tconstruct.library.transfer.ModifiableItemHandler;
import slimeknights.tconstruct.library.transfer.SlotStorageItemHandler;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Inventory composite made of a set of melting module inventories
 */
public class MeltingModuleInventory {
  /**
   * There are {@link Short#MAX_VALUE} data slots in a standard container instance.
   * The smeltery requires 6 slots for fuel syncing. It also requires 3 slots per inventory slot.
   * Thus, the largest number of slots we can have without breaking syncing is the below equation.
   * This does leave us with 1 extra unused slot. If we add anything that uses more slots we will want to adjust this number.
   */
  private static final int MAX_SIZE = (Short.MAX_VALUE - 7) / 3;
  private static final String TAG_SLOT = "slot";
  private static final String TAG_ITEMS = "items";
  private static final String TAG_SIZE = "size";

  /** Parent tile entity */
  private final MantleBlockEntity parent;
  /** Fluid handler for outputs */
  protected final ResourceHandler<FluidResource> fluidHandler;
  /** Array of modules containing each slot */
  private MeltingModule[] modules;
  /** If true, module cannot be resized */
  private final boolean strictSize;
  /** Number of nuggets to produce when melting an ore */
  private final IOreRate oreRate;
  /** Native view of this inventory, registered as the item capability. Lazy as subclasses may not be ready in the constructor */
  private SlotStorageItemHandler itemCapability;

  /**
   * Gets the native item handler of this inventory, registered as the block's item capability.
   * PORT 1.21.10 (transfer step 1): slots change silently inside transactions and replay through {@link #setStackInSlot(int, ItemStack)}
   * on commit, so simulated inserts never sync, look up recipes or reset melting progress.
   */
  public ResourceHandler<ItemResource> getItemCapability() {
    if (itemCapability == null) {
      itemCapability = new SlotStorageItemHandler() {
        @Override
        public int size() {
          return getSlots();
        }

        @Override
        protected ItemStack getStack(int slot) {
          return getStackInSlot(slot);
        }

        @Override
        protected void setStackSilently(int slot, ItemStack stack) {
          if (stack.isEmpty()) {
            if (modules[slot] != null) {
              modules[slot].setStackSilently(ItemStack.EMPTY);
            }
          } else {
            getModule(slot).setStackSilently(stack);
          }
        }

        @Override
        protected void setStackWithSideEffects(int slot, ItemStack stack) {
          // qualified: the unqualified call resolves to SlotStorageItemHandler#setStackInSlot, which calls back here forever
          MeltingModuleInventory.this.setStackInSlot(slot, stack);
        }

        @Override
        protected int getSlotCapacity(int slot, ItemResource resource) {
          return getSlotLimit(slot);
        }

        @Override
        protected boolean isItemValid(int slot, ItemResource resource) {
          return MeltingModuleInventory.this.isItemValid(slot, resource.toStack());
        }
      };
    }
    return itemCapability;
  }

  /** Gets a handler over this inventory for menu slots, set through {@link #setStackInSlot(int, ItemStack)} */
  public ModifiableItemHandler getModifiableItemHandler() {
    getItemCapability();
    return ModifiableItemHandler.of(itemCapability, this::setStackInSlot);
  }

  /**
   * Creates a new inventory with a fixed size
   * @param parent         Parent tile
   * @param fluidHandler   Tank for output
   * @param oreRate        Ore rate
   * @param size           Size
   */
  public MeltingModuleInventory(MantleBlockEntity parent, ResourceHandler<FluidResource> fluidHandler, IOreRate oreRate, int size) {
    this.parent = parent;
    this.fluidHandler = fluidHandler;
    this.modules = new MeltingModule[size];
    this.oreRate = oreRate;
    this.strictSize = size != 0;
  }

  /**
   * Creates a new inventory with a variable size
   * @param parent         Parent tile
   * @param fluidHandler   Tank for output
   * @param oreRate        Ore rate
   */
  public MeltingModuleInventory(MantleBlockEntity parent, ResourceHandler<FluidResource> fluidHandler, IOreRate oreRate) {
    this(parent, fluidHandler, oreRate, 0);
  }

  /* Properties */

  public int getSlots() {
    return modules.length;
  }

  /**
   * Checks if the given slot index is valid
   * @param slot  Slot index to check
   * @return  True if valid
   */
  public boolean validSlot(int slot) {
    return slot >= 0 && slot < getSlots();
  }

  public int getSlotLimit(int slot) {
    return 1;
  }

  public boolean isItemValid(int slot, ItemStack stack) {
    return true;
  }

  /** Returns true if a slot is defined in the array */
  private boolean hasModule(int slot) {
    return validSlot(slot) && modules[slot] != null;
  }

  /**
   * Gets the current time of a slot
   * @param slot  Slot index
   * @return  Slot temperature
   */
  public int getCurrentTime(int slot) {
    return hasModule(slot) ? modules[slot].getCurrentTime() : 0;
  }

  /**
   * Gets the required time for a slot
   * @param slot  Slot index
   * @return  Required time
   */
  public int getRequiredTime(int slot) {
    return hasModule(slot) ? modules[slot].getRequiredTime() : 0;
  }

  /**
   * Gets the required temperature for a slot
   * @param slot  Slot index
   * @return  Required temperature
   */
  public int getRequiredTemp(int slot) {
    return hasModule(slot) ? modules[slot].getRequiredTemp() : 0;
  }


  /* Sub modules */

  /**
   * Gets the module for the given index
   * @param slot  Index
   * @return  Module for index
   * @throws IndexOutOfBoundsException  index is invalid
   */
  public MeltingModule getModule(int slot) {
    if (!validSlot(slot)) {
      throw new IndexOutOfBoundsException();
    }
    if (modules[slot] == null) {
      modules[slot] = new MeltingModule(parent, recipe -> tryFillTank(slot, recipe), oreRate, slot);
    }
    return modules[slot];
  }

  /**
   * Resizes the module to a new size
   * @param newSize        New size
   * @param stackConsumer  Consumer for any stacks that no longer fit
   * @throws IllegalStateException  If this inventory cannot be resized
   */
  public void resize(int newSize, Consumer<ItemStack> stackConsumer) {
    if (strictSize) {
      throw new IllegalStateException("Cannot resize this melting module inventory");
    }
    if (newSize > MAX_SIZE) {
      newSize = MAX_SIZE;
    }
    // nothing to do
    if (newSize == modules.length) {
      return;
    }
    // if shrinking, drop extra items
    if (newSize < modules.length) {
      for (int i = newSize; i < modules.length; i++) {
        if (modules[i] != null && !modules[i].getStack().isEmpty()) {
          stackConsumer.accept(modules[i].getStack());
        }
      }
    }

    // resize the module array
    modules = Arrays.copyOf(modules, newSize);
    parent.setChangedFast();
  }


  /* Item handling */

  public ItemStack getStackInSlot(int slot) {
    if (validSlot(slot)) {
      // don't create the slot, just reading
      if (modules[slot] != null) {
        return modules[slot].getStack();
      }
    }
    return ItemStack.EMPTY;
  }

  public void setStackInSlot(int slot, ItemStack stack) {
    // actually set the stack
    if (validSlot(slot)) {
      if (stack.isEmpty()) {
        if (modules[slot] != null) {
          modules[slot].setStack(ItemStack.EMPTY);
        }
      } else {
        // validate size
        if (stack.getCount() > 1) {
          stack.setCount(1);
        }
        getModule(slot).setStack(stack);
      }
    }
  }

  /* Heating */

  /**
   * Checks if any slot can heat
   * @param temperature  Temperature to try
   * @return  True if a slot can heat
   */
  public boolean canHeat(int temperature) {
    for (MeltingModule module : modules) {
      if (module != null && module.canHeatItem(temperature)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Tries to fill the fluid handler with the given fluid
   * @param index   Index of the module being filled
   * @param recipe  Recipe to add
   * @return  True if filled, false if not enough space for the whole fluid
   */
  protected boolean tryFillTank(int index, IMeltingRecipe recipe) {
    FluidStack fluid = recipe.getOutput(getModule(index));
    // the whole fluid must fit, else nothing is filled; melting ticks are never inside another transaction
    // an empty output always "fits", like the legacy fill returning 0 for it
    if (fluid.isEmpty()) {
      return true;
    }
    try (Transaction tx = Transaction.openRoot()) {
      if (fluidHandler.insert(FluidResource.of(fluid), fluid.getAmount(), tx) == fluid.getAmount()) {
        tx.commit();
        return true;
      }
    }
    return false;
  }

  /**
   * Heats all items in the inventory
   * @param temperature  Heating structure temperature
   */
  public void heatItems(int temperature, int rate) {
    for (MeltingModule module : modules) {
      if (module != null) {
        module.heatItem(temperature, rate);
      }
    }
  }

  /**
   * Cools down all items in the inventory, used when there is no fuel
   */
  public void coolItems() {
    for (MeltingModule module : modules) {
      if (module != null) {
        module.coolItem();
      }
    }
  }

  /**
   * Writes this module to Tag
   * @param nbt  Output to write to
   */
  public void writeToTag(ValueOutput nbt) {
    ValueOutput.ValueOutputList list = nbt.childrenList(TAG_ITEMS);
    for (int i = 0; i < modules.length; i++) {
      if (modules[i] != null && !modules[i].getStack().isEmpty()) {
        ValueOutput moduleTag = list.addChild();
        modules[i].writeToTag(moduleTag);
        moduleTag.putInt(TAG_SLOT, i);
      }
    }
    // list is omitted when empty, as before
    if (list.isEmpty()) {
      nbt.discard(TAG_ITEMS);
    }
    nbt.putInt(TAG_SIZE, modules.length);
  }

  /**
   * Reads this inventory from Tag
   * @param nbt  Tag compound
   */
  public void readFromTag(ValueInput nbt) {
    if (!strictSize) {
      int newSize = readUnsigned(nbt, TAG_SIZE);
      if (newSize != modules.length) {
        modules = Arrays.copyOf(modules, newSize);
      }
    }
    // remove old data
    for (MeltingModule module : modules) {
      if (module != null) {
        module.setStack(ItemStack.EMPTY);
      }
    }

    for (ValueInput item : nbt.childrenListOrEmpty(TAG_ITEMS)) {
      // any numeric: old saves wrote (unsigned) bytes - mask them; new saves write ints
      if (item.getInt(TAG_SLOT).isPresent()) {
        int slot = readUnsigned(item, TAG_SLOT);
        if (validSlot(slot)) {
          getModule(slot).readFromTag(item);
        }
      }
    }
  }

  /** Reads an int that may have been saved as an unsigned byte by old versions */
  private static int readUnsigned(ValueInput input, String key) {
    // old saves wrote a byte, mask it so values above 127 stay unsigned; new saves write ints
    return input.read(key, ExtraCodecs.NBT).orElse(null) instanceof ByteTag byteTag ? byteTag.byteValue() & 255 : input.getIntOr(key, 0);
  }


  /* Container sync */

  /**
   * Sets up all sub slots for tracking
   * @param consumer  IIntArray consumer
   */
  public void trackInts(Consumer<ContainerData> consumer) {
    for (int i = 0; i < getSlots(); i++) {
      consumer.accept(getModule(i));
    }
  }
}
