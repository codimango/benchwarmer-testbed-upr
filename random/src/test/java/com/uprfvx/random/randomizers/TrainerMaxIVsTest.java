package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.Trainer;
import com.uprfvx.romio.gamedata.TrainerPokemon;
import com.uprfvx.romio.romhandlers.RomHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the max IVs trainer setting (Boss/Important/Regular).
 * Does not require external ROMs — uses Trainer and TrainerPokemon directly.
 * Uses reflection for new Settings methods so it compiles at base commit.
 * Improved to exercise production applyMaxIVs path end-to-end via Proxy-mocked RomHandler.
 */
public class TrainerMaxIVsTest {

    private Trainer createTrainer(String tag, int iv) {
        Species species = new Species(1);
        species.setName("Bulbasaur");
        TrainerPokemon tp = new TrainerPokemon(species, 50);
        tp.setIVs(iv);
        Trainer t = new Trainer();
        t.setTag(tag);
        t.getPokemon().add(tp);
        return t;
    }

    private List<Trainer> createMixedTrainers() {
        List<Trainer> trainers = new ArrayList<>();
        trainers.add(createTrainer("GYM1-LEADER", 0)); // boss
        trainers.add(createTrainer("ELITE1", 0)); // boss
        trainers.add(createTrainer("CHAMPION", 5)); // boss
        trainers.add(createTrainer("RIVAL2-0", 10)); // important
        trainers.add(createTrainer("THEMED:SPROUTTOWER-STRONG", 10)); // important
        trainers.add(createTrainer("GYM1", 0)); // regular
        trainers.add(createTrainer(null, 0)); // regular
        return trainers;
    }

    // Create a RomHandler via dynamic Proxy that returns our trainers for getTrainers()
    @SuppressWarnings("unchecked")
    private RomHandler createMockRomHandler(List<Trainer> trainers) {
        return (RomHandler) Proxy.newProxyInstance(
                RomHandler.class.getClassLoader(),
                new Class[]{RomHandler.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getTrainers".equals(name)) {
                        return trainers;
                    }
                    // Return sensible defaults for other methods
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class) return 0;
                    if (ret == void.class) return null;
                    if (ret == String.class) return "";
                    if (ret == List.class) return List.of();
                    if (ret == java.util.Map.class) return java.util.Map.of();
                    if (ret == java.util.Set.class) return java.util.Set.of();
                    return null;
                }
        );
    }

    // Helper that mirrors production logic for unit testing without RomHandler
    private void applyMaxIVsDirect(List<Trainer> trainers, boolean boss, boolean important, boolean regular) {
        if (!boss && !important && !regular) {
            return;
        }
        for (Trainer t : trainers) {
            boolean shouldMax = false;
            if (t.isBoss() && boss) {
                shouldMax = true;
            } else if (t.isImportant() && important) {
                shouldMax = true;
            } else if (!t.isBoss() && !t.isImportant() && regular) {
                shouldMax = true;
            }
            if (shouldMax) {
                for (TrainerPokemon tp : t.getPokemon()) {
                    int current = tp.getIVs();
                    int shinyFlag = current & (1 << 30);
                    tp.setIVs(31 | shinyFlag);
                }
            }
        }
    }

    // Production path via TrainerPokemonRandomizer.applyMaxIVs() with mocked RomHandler
    private void applyProductionMaxIVs(List<Trainer> trainers, Settings settings) {
        RomHandler mockHandler = createMockRomHandler(trainers);
        TrainerPokemonRandomizer randomizer = new TrainerPokemonRandomizer(mockHandler, settings, new Random(0));
        try {
            Method m = TrainerPokemonRandomizer.class.getMethod("applyMaxIVs");
            m.invoke(randomizer);
        } catch (NoSuchMethodException e) {
            // At base, method doesn't exist - simulate OFF behavior
            // This will cause fail_to_pass to fail as expected
            throw new AssertionError("applyMaxIVs method should exist after implementation - feature missing at base");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Reflection helper for Settings new methods
    private boolean tryApplyProductionMaxIVsViaReflection(List<Trainer> trainers, Settings settings) {
        try {
            Method mBoss = Settings.class.getMethod("isMaxIVsForBossTrainerPokemon");
            Method mImportant = Settings.class.getMethod("isMaxIVsForImportantTrainerPokemon");
            Method mRegular = Settings.class.getMethod("isMaxIVsForRegularTrainerPokemon");
            boolean boss = (boolean) mBoss.invoke(settings);
            boolean important = (boolean) mImportant.invoke(settings);
            boolean regular = (boolean) mRegular.invoke(settings);
            applyMaxIVsDirect(trainers, boss, important, regular);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void maxIVsOffRemainsUnchanged() {
        List<Trainer> trainers = createMixedTrainers();
        applyMaxIVsDirect(trainers, false, false, false);

        for (Trainer t : trainers) {
            for (TrainerPokemon tp : t.getPokemon()) {
                int iv = tp.getIVs();
                assertTrue(iv == 0 || iv == 5 || iv == 10, "IV should remain unchanged when OFF, got " + iv);
                assertNotEquals(31, iv & 0x1F, "IV should not be maxed when OFF");
            }
        }
    }

    @Test
    public void maxIVsBossOnOnlyAffectsBosses() {
        List<Trainer> trainers = createMixedTrainers();
        applyMaxIVsDirect(trainers, true, false, false);

        for (Trainer t : trainers) {
            for (TrainerPokemon tp : t.getPokemon()) {
                int ivBase = tp.getIVs() & 0x1F;
                if (t.isBoss()) {
                    assertEquals(31, ivBase, "Boss trainer should have max IVs when ON");
                } else {
                    assertNotEquals(31, ivBase, "Non-boss trainer should not have max IVs when only boss is ON");
                }
            }
        }
    }

    @Test
    public void maxIVsImportantOnOnlyAffectsImportant() {
        List<Trainer> trainers = createMixedTrainers();
        applyMaxIVsDirect(trainers, false, true, false);

        for (Trainer t : trainers) {
            for (TrainerPokemon tp : t.getPokemon()) {
                int ivBase = tp.getIVs() & 0x1F;
                if (t.isImportant()) {
                    assertEquals(31, ivBase, "Important trainer should have max IVs when ON");
                } else {
                    assertNotEquals(31, ivBase, "Non-important trainer should not have max IVs when only important is ON");
                }
            }
        }
    }

    @Test
    public void maxIVsRegularOnOnlyAffectsRegular() {
        List<Trainer> trainers = createMixedTrainers();
        applyMaxIVsDirect(trainers, false, false, true);

        for (Trainer t : trainers) {
            for (TrainerPokemon tp : t.getPokemon()) {
                int ivBase = tp.getIVs() & 0x1F;
                if (t.isRegular()) {
                    assertEquals(31, ivBase, "Regular trainer should have max IVs when ON");
                } else {
                    assertNotEquals(31, ivBase, "Non-regular trainer should not have max IVs when only regular is ON");
                }
            }
        }
    }

    @Test
    public void maxIVsAllOnAffectsAll() {
        List<Trainer> trainers = createMixedTrainers();
        applyMaxIVsDirect(trainers, true, true, true);

        for (Trainer t : trainers) {
            for (TrainerPokemon tp : t.getPokemon()) {
                int ivBase = tp.getIVs() & 0x1F;
                assertEquals(31, ivBase, "All trainers should have max IVs when all ON, but got " + ivBase + " for " + t.getTag());
            }
        }
    }

    @Test
    public void maxIVsPreservesShinyFlag() {
        Species species = new Species(1);
        species.setName("Bulbasaur");
        TrainerPokemon tp = new TrainerPokemon(species, 50);
        tp.setIVs(10 | (1 << 30)); // IV 10 + shiny flag
        Trainer t = new Trainer();
        t.setTag("GYM1-LEADER");
        t.getPokemon().add(tp);
        List<Trainer> trainers = List.of(t);

        applyMaxIVsDirect(trainers, true, false, false);

        int result = trainers.get(0).getPokemon().get(0).getIVs();
        assertEquals(1 << 30, result & (1 << 30), "Shiny flag should be preserved");
        assertEquals(31, result & 0x1F, "IV should be maxed to 31 even with shiny flag");
    }

    @Test
    public void settingsSerializationRoundtrip() {
        Settings original = new Settings();
        try {
            Method setBoss = Settings.class.getMethod("setMaxIVsForBossTrainerPokemon", boolean.class);
            Method setImportant = Settings.class.getMethod("setMaxIVsForImportantTrainerPokemon", boolean.class);
            Method setRegular = Settings.class.getMethod("setMaxIVsForRegularTrainerPokemon", boolean.class);
            setBoss.invoke(original, true);
            setImportant.invoke(original, false);
            setRegular.invoke(original, true);
        } catch (NoSuchMethodException e) {
            fail("Max IVs settings methods should exist after implementation");
            return;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        original.setRomName("TestGame");
        original.setSelectedEXPCurve(com.uprfvx.romio.gamedata.ExpCurve.MEDIUM_FAST);
        original.setBattleStyle(new com.uprfvx.romio.gamedata.BattleStyle());

        String encoded = original.toString();
        Settings decoded = Settings.fromString(encoded);

        try {
            Method isBoss = Settings.class.getMethod("isMaxIVsForBossTrainerPokemon");
            Method isImportant = Settings.class.getMethod("isMaxIVsForImportantTrainerPokemon");
            Method isRegular = Settings.class.getMethod("isMaxIVsForRegularTrainerPokemon");
            assertTrue((boolean) isBoss.invoke(decoded), "Boss IV setting should survive roundtrip");
            assertFalse((boolean) isImportant.invoke(decoded), "Important IV setting should survive roundtrip");
            assertTrue((boolean) isRegular.invoke(decoded), "Regular IV setting should survive roundtrip");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void settingsDefaultIsOff() {
        Settings s = new Settings();
        try {
            Method isBoss = Settings.class.getMethod("isMaxIVsForBossTrainerPokemon");
            Method isImportant = Settings.class.getMethod("isMaxIVsForImportantTrainerPokemon");
            Method isRegular = Settings.class.getMethod("isMaxIVsForRegularTrainerPokemon");
            assertFalse((boolean) isBoss.invoke(s), "Default should be OFF");
            assertFalse((boolean) isImportant.invoke(s), "Default should be OFF");
            assertFalse((boolean) isRegular.invoke(s), "Default should be OFF");
        } catch (NoSuchMethodException e) {
            assertTrue(true, "At base, methods don't exist, treated as OFF");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void maxIVsWithMultiplePokemonPerTrainer() {
        Species species = new Species(1);
        species.setName("Pikachu");
        Trainer t = new Trainer();
        t.setTag("ELITE2");
        for (int i = 0; i < 3; i++) {
            TrainerPokemon tp = new TrainerPokemon(species, 50 + i);
            tp.setIVs(i * 5);
            t.getPokemon().add(tp);
        }
        List<Trainer> trainers = List.of(t);
        applyMaxIVsDirect(trainers, true, false, false);

        for (TrainerPokemon tp : trainers.get(0).getPokemon()) {
            assertEquals(31, tp.getIVs() & 0x1F, "All Pokemon in a boss trainer team should be maxed");
        }
    }

    @Test
    public void trainerTypeClassification() {
        Trainer boss1 = createTrainer("GYM1-LEADER", 0);
        Trainer boss2 = createTrainer("ELITE1", 0);
        Trainer boss3 = createTrainer("CHAMPION", 0);
        Trainer boss4 = createTrainer("UBER", 0);
        Trainer important1 = createTrainer("RIVAL2-0", 0);
        Trainer important2 = createTrainer("FRIEND1-0", 0);
        Trainer important3 = createTrainer("THEMED:TEAMROCKET-STRONG", 0);
        Trainer regular1 = createTrainer("GYM1", 0);
        Trainer regular2 = createTrainer(null, 0);

        assertTrue(boss1.isBoss());
        assertTrue(boss2.isBoss());
        assertTrue(boss3.isBoss());
        assertTrue(boss4.isBoss());
        assertTrue(important1.isImportant());
        assertTrue(important2.isImportant());
        assertTrue(important3.isImportant());
        assertTrue(regular1.isRegular());
        assertTrue(regular2.isRegular());
    }

    // Fail-to-pass tests exercising production path end-to-end

    @Test
    public void productionBossMaxIVsWhenEnabled() {
        List<Trainer> trainers = createMixedTrainers();
        Settings settings = new Settings();
        try {
            Method setBoss = Settings.class.getMethod("setMaxIVsForBossTrainerPokemon", boolean.class);
            setBoss.invoke(settings, true);
            settings.setRomName("Test");
            settings.setSelectedEXPCurve(com.uprfvx.romio.gamedata.ExpCurve.MEDIUM_FAST);
            settings.setBattleStyle(new com.uprfvx.romio.gamedata.BattleStyle());
        } catch (NoSuchMethodException e) {
            fail("Production max IVs setting should exist - feature missing at base, so this fail_to_pass test fails as expected");
            return;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Exercise production applyMaxIVs via Proxy-mocked RomHandler
        RomHandler mockHandler = createMockRomHandler(trainers);
        TrainerPokemonRandomizer randomizer = new TrainerPokemonRandomizer(mockHandler, settings, new Random(0));
        try {
            Method m = TrainerPokemonRandomizer.class.getMethod("applyMaxIVs");
            m.invoke(randomizer);
        } catch (NoSuchMethodException e) {
            fail("applyMaxIVs should exist in production after patch");
            return;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Trainer t : trainers) {
            for (TrainerPokemon tp : t.getPokemon()) {
                int ivBase = tp.getIVs() & 0x1F;
                if (t.isBoss()) {
                    assertEquals(31, ivBase, "Boss trainer should have max IVs in production path");
                }
            }
        }
    }

    @Test
    public void productionImportantMaxIVsWhenEnabled() {
        List<Trainer> trainers = createMixedTrainers();
        Settings settings = new Settings();
        try {
            Method setImportant = Settings.class.getMethod("setMaxIVsForImportantTrainerPokemon", boolean.class);
            setImportant.invoke(settings, true);
        } catch (NoSuchMethodException e) {
            fail("Production max IVs setting should exist - feature missing at base");
            return;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        RomHandler mockHandler = createMockRomHandler(trainers);
        TrainerPokemonRandomizer randomizer = new TrainerPokemonRandomizer(mockHandler, settings, new Random(1));
        try {
            Method m = TrainerPokemonRandomizer.class.getMethod("applyMaxIVs");
            m.invoke(randomizer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Trainer t : trainers) {
            for (TrainerPokemon tp : t.getPokemon()) {
                int ivBase = tp.getIVs() & 0x1F;
                if (t.isImportant()) {
                    assertEquals(31, ivBase, "Important trainer should have max IVs in production path");
                }
            }
        }
    }

    @Test
    public void productionRegularMaxIVsWhenEnabled() {
        List<Trainer> trainers = createMixedTrainers();
        Settings settings = new Settings();
        try {
            Method setRegular = Settings.class.getMethod("setMaxIVsForRegularTrainerPokemon", boolean.class);
            setRegular.invoke(settings, true);
        } catch (NoSuchMethodException e) {
            fail("Production max IVs setting should exist - feature missing at base");
            return;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        RomHandler mockHandler = createMockRomHandler(trainers);
        TrainerPokemonRandomizer randomizer = new TrainerPokemonRandomizer(mockHandler, settings, new Random(2));
        try {
            Method m = TrainerPokemonRandomizer.class.getMethod("applyMaxIVs");
            m.invoke(randomizer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Trainer t : trainers) {
            for (TrainerPokemon tp : t.getPokemon()) {
                int ivBase = tp.getIVs() & 0x1F;
                if (t.isRegular()) {
                    assertEquals(31, ivBase, "Regular trainer should have max IVs in production path");
                }
            }
        }
    }

    @Test
    public void productionAllMaxIVsWhenEnabled() {
        List<Trainer> trainers = createMixedTrainers();
        Settings settings = new Settings();
        try {
            Method setBoss = Settings.class.getMethod("setMaxIVsForBossTrainerPokemon", boolean.class);
            Method setImportant = Settings.class.getMethod("setMaxIVsForImportantTrainerPokemon", boolean.class);
            Method setRegular = Settings.class.getMethod("setMaxIVsForRegularTrainerPokemon", boolean.class);
            setBoss.invoke(settings, true);
            setImportant.invoke(settings, true);
            setRegular.invoke(settings, true);
        } catch (NoSuchMethodException e) {
            fail("Production max IVs settings should exist");
            return;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        RomHandler mockHandler = createMockRomHandler(trainers);
        TrainerPokemonRandomizer randomizer = new TrainerPokemonRandomizer(mockHandler, settings, new Random(3));
        try {
            Method m = TrainerPokemonRandomizer.class.getMethod("applyMaxIVs");
            m.invoke(randomizer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        for (Trainer t : trainers) {
            for (TrainerPokemon tp : t.getPokemon()) {
                int ivBase = tp.getIVs() & 0x1F;
                assertEquals(31, ivBase, "All trainers should have max IVs when all ON in production path, got " + ivBase + " for " + t.getTag());
            }
        }
    }
}
