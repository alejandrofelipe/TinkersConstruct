package slimeknights.tconstruct.common.recipe;

/** Recipe that populates a static lookup at reload. Implemented in place of constructor self-registration; called by {@link RecipeLookupPopulator}. */
public interface ILookupRegistrar {
  /** Populates the static lookups derived from this recipe. */
  void registerLookups();
}
