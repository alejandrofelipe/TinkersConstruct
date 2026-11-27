package slimeknights.mantle.client.uitest;

import net.minecraft.world.level.storage.LevelSummary;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The startup screens the uitest suite gets past on its own: which world backup prompts it confirms, which mod loading warnings fail it */
class UiTestSuiteTest {
  /** Vanilla titles the backup prompt {@code selectWorld.backupQuestion.<status>} ({@code WorldOpenFlows#openWorldCheckVersionCompatibility}) */
  @Test
  void confirmsOnlyNonSevereUpgradePrompts() {
    for (LevelSummary.BackupStatus status : LevelSummary.BackupStatus.values()) {
      if (status.shouldBackup()) {
        assertThat(UiTestSuite.isPlainUpgradePrompt("selectWorld.backupQuestion." + status.getTranslationKey()))
          .as(status.name()).isEqualTo(!status.isSevere());
      }
    }
    assertThat(UiTestSuite.isPlainUpgradePrompt("selectWorld.backupQuestion.downgrade")).isFalse();
    // WorldOpenFlows#askForBackup: experimental settings or an old customized world
    assertThat(UiTestSuite.isPlainUpgradePrompt("selectWorld.backupQuestion.experimental")).isFalse();
    assertThat(UiTestSuite.isPlainUpgradePrompt("selectWorld.backupQuestion.customized")).isFalse();
    assertThat(UiTestSuite.isPlainUpgradePrompt("")).isFalse();
  }

  @Test
  void ownModWarningsKeepsOnlyOurMods() {
    // the canonical constructor: withAffectedMod also reads the mod's file, which the stub doesn't have
    ModLoadingIssue ours = new ModLoadingIssue(ModLoadingIssue.Severity.WARNING, "fml.modloadingissue.test", List.of("a"), null, null, null, mod("tconstruct"));
    ModLoadingIssue theirs = new ModLoadingIssue(ModLoadingIssue.Severity.WARNING, "fml.modloadingissue.other", List.of(), null, null, null, mod("craftingtweaks"));
    ModLoadingIssue unattributed = ModLoadingIssue.warning("fml.modloadingissue.none");
    List<String> own = UiTestSuite.ownModWarnings(List.of(ours, theirs, unattributed), Set.of("mantle", "tconstruct"));
    assertThat(own).containsExactly("tconstruct: fml.modloadingissue.test [a]");
    assertThat(UiTestSuite.ownModWarnings(List.of(theirs, unattributed), Set.of("mantle", "tconstruct"))).isEmpty();
  }

  @Test
  void ownModWarningsMatchesIssuesAttributedByModFile() {
    // no affected mod, only the file: any mod of the file counts
    ModLoadingIssue ourFile = new ModLoadingIssue(ModLoadingIssue.Severity.WARNING, "fml.modloadingissue.file", List.of(), null, null, modFile("other", "mantle"), null);
    ModLoadingIssue theirFile = new ModLoadingIssue(ModLoadingIssue.Severity.WARNING, "fml.modloadingissue.file", List.of(), null, null, modFile("balm"), null);
    // the affected mod wins over its file
    ModLoadingIssue theirModInOurFile = new ModLoadingIssue(ModLoadingIssue.Severity.WARNING, "fml.modloadingissue.mod", List.of(), null, null, modFile("tconstruct"), mod("craftingtweaks"));
    List<String> own = UiTestSuite.ownModWarnings(List.of(ourFile, theirFile, theirModInOurFile), Set.of("mantle", "tconstruct"));
    assertThat(own).containsExactly("mantle: fml.modloadingissue.file []");
  }

  /** Mod file stub answering only its mod infos */
  private static IModFile modFile(String... ids) {
    List<IModInfo> infos = Arrays.stream(ids).map(UiTestSuiteTest::mod).toList();
    return (IModFile) Proxy.newProxyInstance(IModFile.class.getClassLoader(), new Class<?>[] {IModFile.class}, (proxy, method, args) -> switch (method.getName()) {
      case "getModInfos" -> infos;
      case "toString" -> "file" + infos;
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> proxy == args[0];
      default -> throw new UnsupportedOperationException(method.getName());
    });
  }

  /** Mod info stub answering only its mod id */
  private static IModInfo mod(String id) {
    return (IModInfo) Proxy.newProxyInstance(IModInfo.class.getClassLoader(), new Class<?>[] {IModInfo.class}, (proxy, method, args) -> switch (method.getName()) {
      case "getModId", "toString" -> id;
      case "hashCode" -> id.hashCode();
      case "equals" -> proxy == args[0];
      default -> throw new UnsupportedOperationException(method.getName());
    });
  }
}
