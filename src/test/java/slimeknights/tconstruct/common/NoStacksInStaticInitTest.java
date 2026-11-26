package slimeknights.tconstruct.common;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import slimeknights.mantle.Mantle;
import slimeknights.tconstruct.TConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No stack creation in static initializers (M7 plan D-STATIC, Phase 0.5c guard 2). From MC 26.1, {@code new ItemStack}
 * and {@code new FluidStack} (and their codecs) fail until the default data components are bound, which happens at the
 * end of every datapack load. A class initialized earlier (mod construction, registration, client setup, a book page)
 * that builds a stack in {@code <clinit>} then crashes or decodes nothing.
 *
 * <p>Scans the compiled bytecode of every Tinkers and Mantle class with ASM (nothing is loaded): the {@code <clinit>} of
 * each class plus every method of the same class it reaches (static helpers, private methods, enum constructors) must not
 * create a stack, call a method returning one, build a transfer resource or parse/decode a codec. Lambdas are lazy and
 * not followed. Reading {@code ItemStack.EMPTY}/{@code FluidStack.EMPTY} is a field read, so it is never flagged.
 */
class NoStacksInStaticInitTest {
  private static final String ITEM_STACK = "net/minecraft/world/item/ItemStack";
  private static final String FLUID_STACK = "net/neoforged/neoforge/fluids/FluidStack";
  private static final Set<String> STACKS = Set.of(ITEM_STACK, FLUID_STACK);
  private static final Set<String> RESOURCES = Set.of("net/neoforged/neoforge/transfer/item/ItemResource", "net/neoforged/neoforge/transfer/fluid/FluidResource");
  private static final Set<String> CODECS = Set.of("com/mojang/serialization/Codec", "com/mojang/serialization/Decoder", "com/mojang/serialization/MapCodec", "com/mojang/serialization/MapDecoder");

  /**
   * Classes whose static init still creates stacks on 1.21.11, where that is harmless (components are bound from
   * registration on). They are the expected-red list of 26.1.2: <strong>M7 Phase 1/2 (D-STATIC) must empty it</strong>,
   * making each one lazy and then deleting its entry. An entry that no longer creates stacks fails the test, so the list
   * can only shrink.
   */
  private static final Map<String, String> KNOWN = Map.of(
    "slimeknights.mantle.client.book.data.content.ContentSmelting", "FUELS: the smelting book page's 28 fuel stacks",
    "slimeknights.tconstruct.gametest.FluidEffectGameTests", "LAVA fluid stack; gametest classes are initialized when the tests register, before any datapack load",
    "slimeknights.tconstruct.gametest.SmelteryTransferGameTests", "GOLD/COPPER/WATER fluid resources, same registration timing",
    "slimeknights.tconstruct.common.data.loot.BlockLootTableProvider", "BLOCK_ENTITY_NBT decodes an NbtProvider (no stack) in a datagen provider; make it lazy with the D-DATAGEN pass"
  );

  @Test
  void noStackCreationInStaticInit() throws IOException, URISyntaxException {
    Map<String, List<String>> hits = new TreeMap<>();
    int tinkers = scan(TConstruct.class, hits);
    int mantle = scan(Mantle.class, hits);
    assertThat(tinkers).as("Tinkers classes scanned in %s", location(TConstruct.class)).isGreaterThan(2000);
    assertThat(mantle).as("Mantle classes scanned in %s", location(Mantle.class)).isGreaterThan(500);

    Map<String, List<String>> unexpected = new TreeMap<>(hits);
    unexpected.keySet().removeAll(KNOWN.keySet());
    Set<String> stale = new TreeSet<>(KNOWN.keySet());
    stale.removeAll(hits.keySet());
    assertThat(unexpected).as("static initializers that create stacks (26.1: default components are unbound there) - make them lazy").isEmpty();
    assertThat(stale).as("KNOWN entries that no longer create stacks - remove them").isEmpty();
  }

  /** Classes folder or jar the class was loaded from */
  private static Path location(Class<?> anchor) throws URISyntaxException {
    return Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
  }

  /** Scans every class file next to the given class (its classes folder or jar), returning how many were read */
  private static int scan(Class<?> anchor, Map<String, List<String>> hits) throws IOException, URISyntaxException {
    Path root = location(anchor);
    int count = 0;
    if (Files.isDirectory(root)) {
      List<Path> files;
      try (Stream<Path> walk = Files.walk(root)) {
        files = walk.filter(path -> path.toString().endsWith(".class")).toList();
      }
      for (Path file : files) {
        try (InputStream in = Files.newInputStream(file)) {
          scanClass(in, hits);
        }
        count++;
      }
    } else {
      try (JarFile jar = new JarFile(root.toFile())) {
        for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
          JarEntry entry = entries.nextElement();
          if (entry.getName().endsWith(".class") && !entry.getName().startsWith("META-INF/")) {
            try (InputStream in = jar.getInputStream(entry)) {
              scanClass(in, hits);
            }
            count++;
          }
        }
      }
    }
    return count;
  }

  /** A method's stack-creating calls and the methods of its own class it calls */
  private record MethodCalls(List<String> creations, Set<String> sameClass) {}

  /** Records the stack creations reachable from the class's static initializer */
  private static void scanClass(InputStream in, Map<String, List<String>> hits) throws IOException {
    ClassReader reader = new ClassReader(in);
    String owner = reader.getClassName();
    Map<String, MethodCalls> methods = new HashMap<>();
    reader.accept(new ClassVisitor(Opcodes.ASM9) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodCalls calls = new MethodCalls(new ArrayList<>(), new HashSet<>());
        methods.put(name + descriptor, calls);
        return new MethodVisitor(Opcodes.ASM9) {
          private int line = -1;

          @Override
          public void visitLineNumber(int lineNumber, org.objectweb.asm.Label start) {
            line = lineNumber;
          }

          @Override
          public void visitMethodInsn(int opcode, String callOwner, String callName, String callDescriptor, boolean isInterface) {
            if (createsStack(callOwner, callName, callDescriptor)) {
              calls.creations().add(name + ":" + line + " " + callOwner.substring(callOwner.lastIndexOf('/') + 1) + "." + callName);
            }
            if (callOwner.equals(owner) && opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKEINTERFACE) {
              calls.sameClass().add(callName + callDescriptor);
            }
          }
        };
      }
    }, ClassReader.SKIP_FRAMES);

    // walk from <clinit> through the class's own static/private methods and constructors
    List<String> found = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Deque<String> queue = new ArrayDeque<>(List.of("<clinit>()V"));
    while (!queue.isEmpty()) {
      MethodCalls calls = methods.get(queue.pop());
      if (calls != null) {
        found.addAll(calls.creations());
        calls.sameClass().stream().filter(seen::add).forEach(queue::add);
      }
    }
    if (!found.isEmpty()) {
      hits.put(owner.replace('/', '.'), found);
    }
  }

  /** True for a call that creates a stack or a transfer resource, or decodes data */
  private static boolean createsStack(String owner, String name, String descriptor) {
    if (name.equals("<init>")) {
      return STACKS.contains(owner);
    }
    if (STACKS.contains(Type.getReturnType(descriptor).getInternalName())) {
      return true;
    }
    if (RESOURCES.contains(owner) && name.equals("of")) {
      return true;
    }
    return CODECS.contains(owner) && (name.equals("parse") || name.equals("decode"));
  }
}
