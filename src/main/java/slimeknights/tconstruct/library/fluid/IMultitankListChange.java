package slimeknights.tconstruct.library.fluid;

import java.util.function.Consumer;

/** Interface to subscribe to changes in the list of tanks in a multitank */
public interface IMultitankListChange {
  /**
   * Adds a listener which runs when the tank order changes to clear relevant caches.
   * <p>
   * Listeners may run while a transaction is being reverted (a native multitank runs them from its journal's revert when an aborted change
   * altered the tank layout), i.e. while the transaction is closing: they must only invalidate state (clear caches, mark dirty), never query
   * a handler, open a transaction or change any journaled state, as the transaction system is in an inconsistent state at that point.
   * @param parent    Object containing the listener
   * @param listener  Consumer running on change, with parent as a parameter. Should not capture the parent instance directy.
   * @param <T>  Parent type
   */
  <T> void addTankListListener(T parent, Consumer<T> listener);

  /**
   * Removes all listeners for the given parent.
   * @param parent  Parent to target
   */
  void removeTankListListeners(Object parent);
}
