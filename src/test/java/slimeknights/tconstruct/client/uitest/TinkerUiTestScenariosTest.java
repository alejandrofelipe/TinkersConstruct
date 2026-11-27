package slimeknights.tconstruct.client.uitest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.mantle.client.uitest.UiTestScenario;
import slimeknights.mantle.client.uitest.UiTestTags;
import slimeknights.tconstruct.test.BaseMcTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Tags of the Tinkers uitest scenarios: the suite would refuse to run a scenario with no or unknown tags, this catches it at build time */
class TinkerUiTestScenariosTest extends BaseMcTest {
  private static final List<UiTestScenario> SCENARIOS = new ArrayList<>();

  @BeforeAll
  static void registerScenarios() {
    // constructing the scenarios only builds their rigs' descriptions; nothing runs until the suite calls prepare
    TinkerUiTestScenarios.registerAll(SCENARIOS::add, true);
  }

  @Test
  void everyScenarioHasOnlyKnownTags() {
    assertThat(SCENARIOS).hasSizeGreaterThan(50);
    Set<String> known = UiTestTags.known();
    for (UiTestScenario scenario : SCENARIOS) {
      assertThat(UiTestTags.problems(scenario.tags(), known)).as(scenario.id().toString()).isEmpty();
    }
  }

  @Test
  void scenarioIdsAreUnique() {
    // the id path names the PNG and the result key, so a duplicate would overwrite another scenario's result
    List<String> ids = SCENARIOS.stream().map(scenario -> scenario.id().toString()).toList();
    assertThat(ids).doesNotHaveDuplicates();
  }

  @Test
  void smokeSetCoversEachArea() {
    List<UiTestScenario> smoke = SCENARIOS.stream().filter(scenario -> scenario.tags().contains(UiTestTags.SMOKE)).toList();
    Set<String> tags = smoke.stream().flatMap(scenario -> scenario.tags().stream()).collect(Collectors.toSet());
    // a station GUI, an in-world render, JEI and the book, and few enough to stay quick
    assertThat(tags).contains(TinkerUiTestScenarios.STATION, UiTestTags.WORLD, UiTestTags.JEI, UiTestTags.BOOK);
    assertThat(smoke).hasSizeLessThanOrEqualTo(6);
  }
}
