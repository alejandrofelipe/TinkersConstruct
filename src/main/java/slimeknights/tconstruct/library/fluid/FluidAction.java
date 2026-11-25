package slimeknights.tconstruct.library.fluid;

/**
 * Whether a fluid operation of Tinkers' own APIs (tool tank hooks, fluid effects) only checks what would happen or applies it.
 * <p>
 * PORT 1.21.10 (transfer phase 6): replaces NeoForge's {@code IFluidHandler.FluidAction}, which goes away with the legacy fluid handler.
 * Same constants and methods, so callers only change the import.
 */
public enum FluidAction {
  /** Only check what would happen, change nothing */
  SIMULATE,
  /** Apply the change */
  EXECUTE;

  /** Checks if this action applies changes */
  public boolean execute() {
    return this == EXECUTE;
  }

  /** Checks if this action only simulates */
  public boolean simulate() {
    return this == SIMULATE;
  }
}
