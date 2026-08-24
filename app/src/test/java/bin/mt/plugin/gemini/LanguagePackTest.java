package bin.mt.plugin.gemini;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Guards the language packs against the two failures nobody notices until a
 * user reports them: a screen showing a raw "{some_key}" because the entry was
 * never added, and a pack quietly accumulating entries no screen asks for.
 *
 * <p>The second half is not tidiness. Every orphan is a line a volunteer
 * translator is asked to translate for a screen that will never show it.
 */
public class LanguagePackTest {

    /** Resolved by MT itself, so they are deliberately absent from our packs. */
    private static final Set<String> MT_BUILT_INS =
            new HashSet<>(Arrays.asList("ok", "cancel", "close", "delete"));

    /**
     * Read by the MT plugin config and by the language catalogue rather than by
     * a literal in a screen, so they cannot be found by scanning sources.
     */
    private static final Pattern EXEMPT_FROM_USAGE =
            Pattern.compile("^(plugin_name|plugin_description|lang_.+)$");

    /**
     * Every mention of an entry: the explicit getString call and the bare
     * "{key}" literal that MT resolves inside preference rows, which is also
     * how the preset tables carry their labels.
     */
    private static final Pattern REFERENCED = Pattern.compile("\"\\{([A-Za-z_0-9-]+)}\"");

    private static final Pattern DECLARED = Pattern.compile("^([A-Za-z_0-9-]+):");

    @Test
    public void everyReferencedKeyIsDeclared() throws IOException {
        Set<String> declared = keysOf(pack("strings.mtl"));
        Set<String> missing = new TreeSet<>();
        for (Path source : javaSources()) {
            Matcher m = REFERENCED.matcher(withoutComments(read(source)));
            while (m.find()) {
                String key = m.group(1);
                if (!declared.contains(key) && !MT_BUILT_INS.contains(key)) {
                    missing.add(key + "  (" + source.getFileName() + ")");
                }
            }
        }
        if (!missing.isEmpty()) {
            fail("strings.mtl is missing " + missing.size() + " entry/entries the code asks for."
                    + " Each would reach the user as a raw {key} on screen:\n  "
                    + String.join("\n  ", missing));
        }
    }

    @Test
    public void noEntryIsOrphaned() throws IOException {
        Set<String> referenced = new HashSet<>();
        for (Path source : javaSources()) {
            Matcher m = REFERENCED.matcher(withoutComments(read(source)));
            while (m.find()) {
                referenced.add(m.group(1));
            }
        }
        Set<String> orphans = new TreeSet<>();
        for (String key : keysOf(pack("strings.mtl"))) {
            if (!referenced.contains(key) && !EXEMPT_FROM_USAGE.matcher(key).matches()) {
                orphans.add(key);
            }
        }
        if (!orphans.isEmpty()) {
            fail("strings.mtl has " + orphans.size() + " entry/entries nothing asks for."
                    + " Remove them rather than asking translators to translate them:\n  "
                    + String.join("\n  ", orphans));
        }
    }

    @Test
    public void translationsDeclareNothingUnknown() throws IOException {
        Set<String> base = keysOf(pack("strings.mtl"));
        for (Path translated : packs()) {
            if (translated.getFileName().toString().equals("strings.mtl")) {
                continue;
            }
            Set<String> unknown = new TreeSet<>(keysOf(translated));
            unknown.removeAll(base);
            if (!unknown.isEmpty()) {
                // Nothing can look up an entry the base pack does not name, so
                // one here is a typo that silently does nothing.
                fail(translated.getFileName() + " declares " + unknown.size()
                        + " entry/entries that strings.mtl does not:\n  "
                        + String.join("\n  ", unknown));
            }
        }
    }

    @Test
    public void everyPackParses() throws IOException {
        for (Path p : packs()) {
            int entries = 0;
            for (String line : read(p).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (!DECLARED.matcher(line).find()) {
                    fail(p.getFileName() + ": neither a comment nor 'key: value' -> " + line);
                }
                entries++;
            }
            assertTrue(p.getFileName() + " is empty", entries > 0);
        }
    }

    // ---- helpers ----

    /** Javadoc and comments quote {0} and {name} as examples; they are not lookups. */
    private static String withoutComments(String source) {
        StringBuilder out = new StringBuilder();
        for (String line : source.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) {
                continue;
            }
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static Path pack(String name) {
        return new File("src/main/assets/" + name).toPath();
    }

    private static List<Path> packs() throws IOException {
        try (Stream<Path> s = Files.list(new File("src/main/assets").toPath())) {
            List<Path> out = new ArrayList<>();
            s.filter(p -> p.getFileName().toString().endsWith(".mtl")).forEach(out::add);
            return out;
        }
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> s = Files.walk(new File("src/main/java").toPath())) {
            List<Path> out = new ArrayList<>();
            s.filter(p -> p.getFileName().toString().endsWith(".java")).forEach(out::add);
            return out;
        }
    }

    private static String read(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    private static Set<String> keysOf(Path pack) throws IOException {
        Set<String> keys = new HashSet<>();
        for (String line : read(pack).split("\n")) {
            Matcher m = DECLARED.matcher(line);
            if (m.find()) {
                keys.add(m.group(1));
            }
        }
        return keys;
    }
}
