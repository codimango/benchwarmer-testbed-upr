package filefunctions;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FileNameFunctionsTest {

    @Test
    public void addsDefaultExtensionWhenThereIsNone() {
        File fixed = FileNameFunctions.fixFilename(new File("rom"), "gba");
        assertEquals("rom.gba", fixed.getName());
    }

    @Test
    public void replacesABannedExtension() {
        File fixed = FileNameFunctions.fixFilename(
                new File("rom.nds"), "gba", Collections.singletonList("nds"));
        assertEquals("rom.gba", fixed.getName());
    }

    @Test
    public void leavesAnUnbannedExtensionAlone() {
        File fixed = FileNameFunctions.fixFilename(new File("rom.gbc"), "gba");
        assertEquals("rom.gbc", fixed.getName());
    }

    @Test
    public void leavesTheDefaultExtensionAlone() {
        File fixed = FileNameFunctions.fixFilename(new File("rom.gba"), "gba");
        assertEquals("rom.gba", fixed.getName());
    }

    @Test
    public void treatsATrailingDotAsNoExtension() {
        File fixed = FileNameFunctions.fixFilename(new File("rom."), "gba");
        assertEquals("rom..gba", fixed.getName());
    }

    @Test
    public void onlyConsidersTheFileNameNotTheDirectory() {
        File original = new File(new File("saves.v2"), "rom");
        File fixed = FileNameFunctions.fixFilename(original, "gba");
        assertEquals("rom.gba", fixed.getName());
    }

    @Test
    public void replacesABannedExtensionEvenWhenOthersAreListed() {
        List<String> banned = List.of("nds", "gbc");
        File fixed = FileNameFunctions.fixFilename(new File("rom.gbc"), "gba", banned);
        assertEquals("rom.gba", fixed.getName());
    }
}
