package com.uprfvx.random.randomizers;

import com.uprfvx.random.Settings;
import com.uprfvx.romio.gamedata.Encounter;
import com.uprfvx.romio.gamedata.EncounterArea;
import com.uprfvx.romio.gamedata.EncounterType;
import com.uprfvx.romio.gamedata.Species;
import com.uprfvx.romio.gamedata.SpeciesSet;
import com.uprfvx.romio.gamedata.Type;
import com.uprfvx.romio.romhandlers.RomHandler;
import com.uprfvx.romio.services.RestrictedSpeciesService;
import com.uprfvx.romio.services.TypeService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the "only Water types for fishing/surfing encounters" setting.
 * Does not require external ROMs - uses a mocked RomHandler, hand-built
 * Species, and hand-built EncounterAreas.
 * Uses reflection for the new setting so the tests compile at the base
 * commit where the setting does not exist yet.
 */
public class WaterOnlyFishingSurfingTest {

    private static final int SEED_COUNT = 30;

    private Species createSpecies(int number, String name, Type primary, Type secondary) {
        Species species = new Species(number);
        species.setName(name);
        species.setPrimaryType(primary);
        species.setSecondaryType(secondary);
        return species;
    }

    private SpeciesSet createPool() {
        SpeciesSet pool = new SpeciesSet();
        pool.add(createSpecies(7, "Squirtle", Type.WATER, null));
        pool.add(createSpecies(120, "Staryu", Type.WATER, null));
        pool.add(createSpecies(130, "Gyarados", Type.WATER, Type.FLYING));
        pool.add(createSpecies(72, "Tentacool", Type.WATER, Type.POISON));
        pool.add(createSpecies(195, "Quagsire", Type.WATER, Type.GROUND));
        pool.add(createSpecies(721, "Volcanion", Type.FIRE, Type.WATER));
        pool.add(createSpecies(140, "Kabuto", Type.ROCK, Type.WATER));
        pool.add(createSpecies(25, "Pikachu", Type.ELECTRIC, null));
        pool.add(createSpecies(4, "Charmander", Type.FIRE, null));
        pool.add(createSpecies(1, "Bulbasaur", Type.GRASS, Type.POISON));
        pool.add(createSpecies(16, "Pidgey", Type.NORMAL, Type.FLYING));
        return pool;
    }

    private EncounterArea createArea(String name, int mapIndex, EncounterType type, SpeciesSet pool, int... levels) {
        EncounterArea area = new EncounterArea();
        area.setIdentifiers(name, mapIndex, type);
        List<Species> choices = new ArrayList<>(pool);
        for (int i = 0; i < levels.length; i++) {
            area.add(new Encounter(choices.get(i % choices.size()), levels[i]));
        }
        return area;
    }

    private List<EncounterArea> createAreas(SpeciesSet pool) {
        List<EncounterArea> areas = new ArrayList<>();
        areas.add(createArea("Route 19 Surf", 19, EncounterType.SURFING, pool, 5, 10, 15));
        areas.add(createArea("Route 12 Fishing", 12, EncounterType.FISHING, pool, 5, 10));
        areas.add(createArea("Route 1 Grass", 1, EncounterType.WALKING, pool, 3, 4, 5));
        return areas;
    }

    private Species speciesByNumber(SpeciesSet pool, int number) {
        for (Species species : pool) {
            if (species.getNumber() == number) {
                return species;
            }
        }
        throw new IllegalArgumentException("No species with number " + number);
    }

    private List<EncounterArea> createMixedMapAreas(SpeciesSet pool) {
        // One map (index 20) with grass and surf areas sharing species, so a
        // 1-to-1 mapping zone spans both water and non-water areas.
        Species pidgey = speciesByNumber(pool, 16);
        Species charmander = speciesByNumber(pool, 4);
        EncounterArea grass = new EncounterArea();
        grass.setIdentifiers("Route 20 Grass", 20, EncounterType.WALKING);
        grass.add(new Encounter(pidgey, 5));
        grass.add(new Encounter(charmander, 5));
        EncounterArea surf = new EncounterArea();
        surf.setIdentifiers("Route 20 Surf", 20, EncounterType.SURFING);
        surf.add(new Encounter(pidgey, 5));
        surf.add(new Encounter(charmander, 5));
        List<EncounterArea> areas = new ArrayList<>();
        areas.add(grass);
        areas.add(surf);
        return areas;
    }

    private List<EncounterArea> createMixedLocationAreas(SpeciesSet pool) {
        // One named location ("Route 20") with grass and surf areas sharing
        // species, so a 1-to-1 mapping zone spans both water and non-water areas.
        List<EncounterArea> areas = createMixedMapAreas(pool);
        for (EncounterArea area : areas) {
            area.setLocationTag("Route 20");
        }
        return areas;
    }

    private List<EncounterArea> createTwoSurfZoneAreas(SpeciesSet pool) {
        // One zone (map 21 / location "Route 21") with grass and two surf
        // areas sharing species, so a 1-to-1 mapping zone spans multiple
        // water areas. surf1 repeats pidgey to check same-species consistency
        // within a single area too.
        Species pidgey = speciesByNumber(pool, 16);
        Species charmander = speciesByNumber(pool, 4);
        EncounterArea grass = new EncounterArea();
        grass.setIdentifiers("Route 21 Grass", 21, EncounterType.WALKING);
        grass.setLocationTag("Route 21");
        grass.add(new Encounter(pidgey, 5));
        grass.add(new Encounter(charmander, 5));
        EncounterArea surf1 = new EncounterArea();
        surf1.setIdentifiers("Route 21 Surf North", 21, EncounterType.SURFING);
        surf1.setLocationTag("Route 21");
        surf1.add(new Encounter(pidgey, 5));
        surf1.add(new Encounter(charmander, 5));
        surf1.add(new Encounter(pidgey, 5));
        EncounterArea surf2 = new EncounterArea();
        surf2.setIdentifiers("Route 21 Surf South", 21, EncounterType.SURFING);
        surf2.setLocationTag("Route 21");
        surf2.add(new Encounter(charmander, 5));
        surf2.add(new Encounter(pidgey, 5));
        List<EncounterArea> areas = new ArrayList<>();
        areas.add(grass);
        areas.add(surf1);
        areas.add(surf2);
        return areas;
    }

    private List<List<Integer>> snapshotOriginals(List<EncounterArea> areas) {
        List<List<Integer>> snapshot = new ArrayList<>();
        for (EncounterArea area : areas) {
            List<Integer> numbers = new ArrayList<>();
            for (Encounter enc : area) {
                numbers.add(enc.getSpecies().getNumber());
            }
            snapshot.add(numbers);
        }
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private RomHandler createMockRomHandler(SpeciesSet pool, List<EncounterArea> areas) {
        final RestrictedSpeciesService[] services = new RestrictedSpeciesService[1];
        final TypeService[] typeServices = new TypeService[1];
        RomHandler proxy = (RomHandler) Proxy.newProxyInstance(
                RomHandler.class.getClassLoader(),
                new Class[]{RomHandler.class},
                (p, method, args) -> {
                    String name = method.getName();
                    if ("getRestrictedSpeciesService".equals(name)) return services[0];
                    if ("getTypeService".equals(name)) return typeServices[0];
                    if ("getEncounters".equals(name)) return areas;
                    if ("setEncounters".equals(name)) return null;
                    if ("getBannedForWildEncounters".equals(name)) return new SpeciesSet();
                    if ("getSpeciesSetInclFormes".equals(name)) return pool;
                    if ("getAltFormes".equals(name)) return new SpeciesSet();
                    if ("isORAS".equals(name)) return false;
                    Class<?> ret = method.getReturnType();
                    if (ret == boolean.class) return false;
                    if (ret == int.class) return 0;
                    if (ret == void.class) return null;
                    if (ret == String.class) return "";
                    if (ret == List.class) return List.of();
                    if (ret == java.util.Map.class) return java.util.Map.of();
                    if (ret == java.util.Set.class) return java.util.Set.of();
                    return null;
                });
        services[0] = new RestrictedSpeciesService(proxy);
        services[0].setRestrictions(null);
        typeServices[0] = new TypeService(proxy);
        return proxy;
    }

    private void setWaterOnly(Settings settings, boolean value) {
        try {
            Method setter = Settings.class.getMethod("setOnlyWaterTypesForFishingSurfing", boolean.class);
            setter.invoke(settings, value);
        } catch (NoSuchMethodException e) {
            fail("Only-water-types setting should exist after implementation - feature missing at base");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isWaterType(Species species) {
        return species.getPrimaryType(false) == Type.WATER
                || (species.hasSecondaryType(false) && species.getSecondaryType(false) == Type.WATER);
    }

    private List<Encounter> encountersOfType(List<EncounterArea> areas, EncounterType type) {
        List<Encounter> out = new ArrayList<>();
        for (EncounterArea area : areas) {
            if (area.getEncounterType() == type) {
                out.addAll(area);
            }
        }
        return out;
    }

    private Settings baseSettings() {
        Settings settings = new Settings();
        settings.setRandomizeWildPokemon(true);
        settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.NONE);
        settings.setUseTimeBasedEncounters(false);
        return settings;
    }

    private List<EncounterArea> runRandomizer(Settings settings, SpeciesSet pool, long seed) {
        List<EncounterArea> areas = createAreas(pool);
        RomHandler handler = createMockRomHandler(pool, areas);
        new WildEncounterRandomizer(handler, settings, new Random(seed)).randomizeEncounters();
        return areas;
    }

    private List<EncounterArea> runRandomizerOnAreas(Settings settings, SpeciesSet pool,
                                                     List<EncounterArea> areas, long seed) {
        RomHandler handler = createMockRomHandler(pool, areas);
        new WildEncounterRandomizer(handler, settings, new Random(seed)).randomizeEncounters();
        return areas;
    }

    @Test
    public void settingsDefaultIsOff() {
        Settings settings = new Settings();
        try {
            Method getter = Settings.class.getMethod("isOnlyWaterTypesForFishingSurfing");
            assertFalse((boolean) getter.invoke(settings), "Default should be OFF");
        } catch (NoSuchMethodException e) {
            assertTrue(true, "At base, method doesn't exist, treated as OFF");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void settingsSerializationRoundtrip() {
        Settings original = new Settings();
        setWaterOnly(original, true);
        original.setRomName("TestGame");
        original.setSelectedEXPCurve(com.uprfvx.romio.gamedata.ExpCurve.MEDIUM_FAST);
        original.setBattleStyle(new com.uprfvx.romio.gamedata.BattleStyle());
        String encoded = original.toString();
        Settings decoded = Settings.fromString(encoded);
        try {
            Method getter = Settings.class.getMethod("isOnlyWaterTypesForFishingSurfing");
            assertTrue((boolean) getter.invoke(decoded), "Setting should survive roundtrip");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void whenOnSurfingEncountersAreWaterOnly() {
        SpeciesSet pool = createPool();
        for (int seed = 0; seed < SEED_COUNT; seed++) {
            Settings settings = baseSettings();
            setWaterOnly(settings, true);
            List<EncounterArea> areas = runRandomizer(settings, pool, seed);
            for (Encounter enc : encountersOfType(areas, EncounterType.SURFING)) {
                assertTrue(isWaterType(enc.getSpecies()),
                        "Surfing encounter should be Water-type but was " + enc.getSpecies()
                                + " (seed " + seed + ")");
            }
        }
    }

    @Test
    public void whenOnFishingEncountersAreWaterOnly() {
        SpeciesSet pool = createPool();
        for (int seed = 0; seed < SEED_COUNT; seed++) {
            Settings settings = baseSettings();
            setWaterOnly(settings, true);
            List<EncounterArea> areas = runRandomizer(settings, pool, seed);
            for (Encounter enc : encountersOfType(areas, EncounterType.FISHING)) {
                assertTrue(isWaterType(enc.getSpecies()),
                        "Fishing encounter should be Water-type but was " + enc.getSpecies()
                                + " (seed " + seed + ")");
            }
        }
    }

    @Test
    public void whenOnSurfingEncountersAreWaterOnlyWithAreaMapping() {
        SpeciesSet pool = createPool();
        for (int seed = 0; seed < SEED_COUNT; seed++) {
            Settings settings = baseSettings();
            settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.ENCOUNTER_SET);
            setWaterOnly(settings, true);
            List<EncounterArea> areas = runRandomizer(settings, pool, seed);
            for (Encounter enc : encountersOfType(areas, EncounterType.SURFING)) {
                assertTrue(isWaterType(enc.getSpecies()),
                        "Surfing encounter should be Water-type but was " + enc.getSpecies()
                                + " (seed " + seed + ")");
            }
            for (Encounter enc : encountersOfType(areas, EncounterType.FISHING)) {
                assertTrue(isWaterType(enc.getSpecies()),
                        "Fishing encounter should be Water-type but was " + enc.getSpecies()
                                + " (seed " + seed + ")");
            }
        }
    }

    @Test
    public void whenOnWalkingEncountersStillRollNonWater() {
        SpeciesSet pool = createPool();
        boolean sawNonWater = false;
        for (int seed = 0; seed < SEED_COUNT && !sawNonWater; seed++) {
            Settings settings = baseSettings();
            setWaterOnly(settings, true);
            List<EncounterArea> areas = runRandomizer(settings, pool, seed);
            for (Encounter enc : encountersOfType(areas, EncounterType.WALKING)) {
                if (!isWaterType(enc.getSpecies())) {
                    sawNonWater = true;
                    break;
                }
            }
        }
        assertTrue(sawNonWater, "Walking areas should still roll non-Water species when ON");
    }

    @Test
    public void whenOnMixedZoneMappingKeepsSurfWaterOnly() {
        SpeciesSet pool = createPool();
        for (int seed = 0; seed < SEED_COUNT; seed++) {
            Settings settings = baseSettings();
            settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.MAP);
            setWaterOnly(settings, true);
            List<EncounterArea> areas = runRandomizerOnAreas(settings, pool, createMixedMapAreas(pool), seed);
            for (Encounter enc : encountersOfType(areas, EncounterType.SURFING)) {
                assertTrue(isWaterType(enc.getSpecies()),
                        "Surfing encounter should be Water-type but was " + enc.getSpecies()
                                + " (seed " + seed + ")");
            }
        }
    }

    @Test
    public void whenOnMixedZoneMappingLeavesGrassUnrestricted() {
        SpeciesSet pool = createPool();
        boolean sawNonWater = false;
        for (int seed = 0; seed < SEED_COUNT && !sawNonWater; seed++) {
            Settings settings = baseSettings();
            settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.MAP);
            setWaterOnly(settings, true);
            List<EncounterArea> areas = runRandomizerOnAreas(settings, pool, createMixedMapAreas(pool), seed);
            for (Encounter enc : encountersOfType(areas, EncounterType.WALKING)) {
                if (!isWaterType(enc.getSpecies())) {
                    sawNonWater = true;
                    break;
                }
            }
        }
        assertTrue(sawNonWater, "Grass areas should still roll non-Water species when ON");
    }

    @Test
    public void whenOnMixedLocationMappingKeepsSurfWaterOnly() {
        SpeciesSet pool = createPool();
        for (int seed = 0; seed < SEED_COUNT; seed++) {
            Settings settings = baseSettings();
            settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.NAMED_LOCATION);
            setWaterOnly(settings, true);
            List<EncounterArea> areas = runRandomizerOnAreas(settings, pool, createMixedLocationAreas(pool), seed);
            for (Encounter enc : encountersOfType(areas, EncounterType.SURFING)) {
                assertTrue(isWaterType(enc.getSpecies()),
                        "Surfing encounter should be Water-type but was " + enc.getSpecies()
                                + " (seed " + seed + ")");
            }
        }
    }

    @Test
    public void whenOnMixedGameMappingKeepsSurfWaterOnly() {
        SpeciesSet pool = createPool();
        for (int seed = 0; seed < SEED_COUNT; seed++) {
            Settings settings = baseSettings();
            settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.GAME);
            setWaterOnly(settings, true);
            List<EncounterArea> areas = runRandomizerOnAreas(settings, pool, createMixedMapAreas(pool), seed);
            for (Encounter enc : encountersOfType(areas, EncounterType.SURFING)) {
                assertTrue(isWaterType(enc.getSpecies()),
                        "Surfing encounter should be Water-type but was " + enc.getSpecies()
                                + " (seed " + seed + ")");
            }
        }
    }

    private void assertWaterMappingConsistent(List<EncounterArea> areas,
                                                List<List<Integer>> originals, int seed) {
        java.util.Map<Integer, Integer> replacementByOriginal = new java.util.HashMap<>();
        for (int a = 0; a < areas.size(); a++) {
            EncounterArea area = areas.get(a);
            if (area.getEncounterType() != EncounterType.SURFING) {
                continue;
            }
            List<Encounter> encounters = new ArrayList<>();
            area.forEach(encounters::add);
            for (int i = 0; i < encounters.size(); i++) {
                Species replacement = encounters.get(i).getSpecies();
                assertTrue(isWaterType(replacement),
                        "Surfing encounter should be Water-type but was " + replacement
                                + " (seed " + seed + ")");
                int original = originals.get(a).get(i);
                int replaced = replacement.getNumber();
                if (replacementByOriginal.containsKey(original)) {
                    assertEquals(replacementByOriginal.get(original).intValue(), replaced,
                            "Same original species should map to the same replacement "
                                    + "within the zone (seed " + seed + ")");
                } else {
                    replacementByOriginal.put(original, replaced);
                }
            }
        }
        assertEquals(replacementByOriginal.size(),
                new java.util.HashSet<>(replacementByOriginal.values()).size(),
                "Distinct original species should map to distinct replacements "
                        + "within the zone (seed " + seed + ")");
    }

    @Test
    public void whenOnMixedZoneMappingIsConsistentAcrossWaterAreas() {
        SpeciesSet pool = createPool();
        for (int seed = 0; seed < SEED_COUNT; seed++) {
            Settings settings = baseSettings();
            settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.MAP);
            setWaterOnly(settings, true);
            List<EncounterArea> areas = createTwoSurfZoneAreas(pool);
            List<List<Integer>> originals = snapshotOriginals(areas);
            runRandomizerOnAreas(settings, pool, areas, seed);
            assertWaterMappingConsistent(areas, originals, seed);
        }
    }

    @Test
    public void whenOnMixedLocationMappingIsConsistentAcrossWaterAreas() {
        SpeciesSet pool = createPool();
        for (int seed = 0; seed < SEED_COUNT; seed++) {
            Settings settings = baseSettings();
            settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.NAMED_LOCATION);
            setWaterOnly(settings, true);
            List<EncounterArea> areas = createTwoSurfZoneAreas(pool);
            List<List<Integer>> originals = snapshotOriginals(areas);
            runRandomizerOnAreas(settings, pool, areas, seed);
            assertWaterMappingConsistent(areas, originals, seed);
        }
    }

    @Test
    public void whenOnMixedGameMappingIsConsistentAcrossWaterAreas() {
        SpeciesSet pool = createPool();
        for (int seed = 0; seed < SEED_COUNT; seed++) {
            Settings settings = baseSettings();
            settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.GAME);
            setWaterOnly(settings, true);
            List<EncounterArea> areas = createTwoSurfZoneAreas(pool);
            List<List<Integer>> originals = snapshotOriginals(areas);
            runRandomizerOnAreas(settings, pool, areas, seed);
            assertWaterMappingConsistent(areas, originals, seed);
        }
    }

    @Test
    public void whenOnAreaMappingIsConsistentWithinWaterArea() {
        SpeciesSet pool = createPool();
        for (int seed = 0; seed < SEED_COUNT; seed++) {
            Settings settings = baseSettings();
            settings.setWildPokemonZoneMod(Settings.WildPokemonZoneMod.ENCOUNTER_SET);
            setWaterOnly(settings, true);
            List<EncounterArea> areas = createTwoSurfZoneAreas(pool);
            List<List<Integer>> originals = snapshotOriginals(areas);
            runRandomizerOnAreas(settings, pool, areas, seed);
            // Each area is its own zone here: check consistency per area.
            for (int a = 0; a < areas.size(); a++) {
                assertWaterMappingConsistent(
                        java.util.Collections.singletonList(areas.get(a)),
                        java.util.Collections.singletonList(originals.get(a)), seed);
            }
        }
    }

    @Test
    public void whenOnWaterReplacementsVaryAcrossSeeds() {
        SpeciesSet pool = createPool();
        for (Settings.WildPokemonZoneMod mode : new Settings.WildPokemonZoneMod[]{
                Settings.WildPokemonZoneMod.NONE, Settings.WildPokemonZoneMod.MAP}) {
            java.util.Set<Integer> seen = new java.util.HashSet<>();
            for (int seed = 0; seed < SEED_COUNT; seed++) {
                Settings settings = baseSettings();
                settings.setWildPokemonZoneMod(mode);
                setWaterOnly(settings, true);
                List<EncounterArea> areas = runRandomizerOnAreas(
                        settings, pool, createTwoSurfZoneAreas(pool), seed);
                for (Encounter enc : encountersOfType(areas, EncounterType.SURFING)) {
                    seen.add(enc.getSpecies().getNumber());
                }
            }
            assertTrue(seen.size() > 1,
                    "Water replacements should vary across seeds in mode " + mode);
        }
    }

    private SpeciesSet createSecondaryWaterMixedPool() {
        // Only the first two are Water (via their secondary type); the rest
        // are not Water at all. Water-only mode must accept the secondary-Water
        // species: a primary-type-only filter would find nothing valid here.
        SpeciesSet pool = new SpeciesSet();
        pool.add(createSpecies(138, "Omanyte", Type.ROCK, Type.WATER));
        pool.add(createSpecies(140, "Kabuto", Type.ROCK, Type.WATER));
        pool.add(createSpecies(25, "Pikachu", Type.ELECTRIC, null));
        pool.add(createSpecies(4, "Charmander", Type.FIRE, null));
        return pool;
    }

    private boolean isSecondaryWater(Species species) {
        return species.getPrimaryType(false) != Type.WATER
                && species.hasSecondaryType(false)
                && species.getSecondaryType(false) == Type.WATER;
    }

    @Test
    public void whenOnSecondaryWaterSpeciesAreAcceptedInWaterAreas() {
        SpeciesSet pool = createSecondaryWaterMixedPool();
        for (Settings.WildPokemonZoneMod mode : new Settings.WildPokemonZoneMod[]{
                Settings.WildPokemonZoneMod.NONE, Settings.WildPokemonZoneMod.ENCOUNTER_SET}) {
            boolean sawSecondaryWater = false;
            for (int seed = 0; seed < SEED_COUNT; seed++) {
                Settings settings = baseSettings();
                settings.setWildPokemonZoneMod(mode);
                setWaterOnly(settings, true);
                List<EncounterArea> areas = runRandomizer(settings, pool, seed);
                for (EncounterType type : new EncounterType[]{
                        EncounterType.SURFING, EncounterType.FISHING}) {
                    for (Encounter enc : encountersOfType(areas, type)) {
                        Species replacement = enc.getSpecies();
                        assertTrue(pool.contains(replacement),
                                "Replacement should come from the allowed pool in mode " + mode
                                        + " (seed " + seed + ")");
                        assertTrue(isWaterType(replacement),
                                "Water-area encounter should be Water-type but was " + replacement
                                        + " in mode " + mode + " (seed " + seed + ")");
                        if (isSecondaryWater(replacement)) {
                            sawSecondaryWater = true;
                        }
                    }
                }
            }
            assertTrue(sawSecondaryWater,
                    "Secondary-Water species should be accepted as Water in mode " + mode);
        }
    }

    @Test
    public void whenOnSecondaryWaterSpeciesAppearAcrossSeeds() {
        SpeciesSet pool = createPool();
        boolean sawKabuto = false;
        for (int seed = 0; seed < SEED_COUNT && !sawKabuto; seed++) {
            Settings settings = baseSettings();
            setWaterOnly(settings, true);
            List<EncounterArea> areas = runRandomizer(settings, pool, seed);
            for (EncounterType type : new EncounterType[]{
                    EncounterType.SURFING, EncounterType.FISHING}) {
                for (Encounter enc : encountersOfType(areas, type)) {
                    if (enc.getSpecies().getNumber() == 140) {
                        sawKabuto = true;
                        break;
                    }
                }
            }
        }
        assertTrue(sawKabuto,
                "Kabuto (Rock/Water) should be selectable in water areas when ON");
    }

    @Test
    public void whenOffSurfingCanRollNonWater() {
        SpeciesSet pool = createPool();
        boolean sawNonWater = false;
        for (int seed = 0; seed < SEED_COUNT && !sawNonWater; seed++) {
            Settings settings = baseSettings();
            List<EncounterArea> areas = runRandomizer(settings, pool, seed);
            for (Encounter enc : encountersOfType(areas, EncounterType.SURFING)) {
                if (!isWaterType(enc.getSpecies())) {
                    sawNonWater = true;
                    break;
                }
            }
        }
        assertTrue(sawNonWater, "Surfing areas should be able to roll non-Water species when OFF");
    }

    @Test
    public void whenOffReplacementsComeFromThePool() {
        SpeciesSet pool = createPool();
        for (int seed = 0; seed < SEED_COUNT; seed++) {
            Settings settings = baseSettings();
            List<EncounterArea> areas = runRandomizer(settings, pool, seed);
            for (EncounterArea area : areas) {
                for (Encounter enc : area) {
                    assertTrue(pool.contains(enc.getSpecies()),
                            "Replacement should come from the allowed pool (seed " + seed + ")");
                }
            }
        }
    }
}
