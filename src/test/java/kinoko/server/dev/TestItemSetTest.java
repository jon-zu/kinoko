package kinoko.server.dev;

import kinoko.provider.ItemProvider;
import kinoko.provider.StringProvider;
import kinoko.provider.SkillProvider;
import kinoko.server.command.CommandProcessor;
import kinoko.world.item.InventoryType;
import kinoko.world.item.WeaponType;
import kinoko.world.job.Job;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_JOB_SEED_WZ", matches = "true")
class TestItemSetTest {
    @BeforeAll
    static void loadWz() {
        ItemProvider.initialize();
        StringProvider.initialize();
        SkillProvider.initialize();
    }

    @Test
    void commandAliasesAreRegistered() {
        CommandProcessor.initialize();
        assertEquals(CommandProcessor.getCommand("test-set").orElseThrow(),
                CommandProcessor.getCommand("testset").orElseThrow());
    }

    @Test
    void equippedAndExistingSeedItemsArePreservedForEveryFinalJob() {
        for (Job job : JobSeeder.JOBS) {
            final var cd = JobSeeder.createCharacter(1, 1, job);
            final int nextSn = cd.getItemSnCounter().get();
            final var result = TestItemSet.giveMissing(cd);
            assertEquals(0, result.added(), job.name());
            assertTrue(result.operations().isEmpty(), job.name());
            assertTrue(result.noSpace().isEmpty(), job.name());
            assertEquals(nextSn, cd.getItemSnCounter().get(), job.name());
        }
    }

    @Test
    void onlyMissingItemsAreAddedAndFullInventoriesCanBeRetried() {
        final var cd = JobSeeder.createCharacter(1, 1, Job.NIGHT_LORD);
        final var im = cd.getInventoryManager();
        final int originalBullets = im.getItemCount(2330000);
        im.getEquipInventory().getItems().clear();
        im.getEquipInventory().setSize(0);
        final var rocks = im.getEtcInventory().getItems().values().stream()
                .filter(item -> item.getItemId() == 4006000).findFirst().orElseThrow();
        rocks.setQuantity((short) 1);
        im.getConsumeInventory().getItems().entrySet().removeIf(entry -> entry.getValue().getItemId() == 2070000);
        final var first = TestItemSet.giveMissing(cd);
        assertEquals(1, first.added()); // Missing stars, even though EQUIP is full.
        assertEquals(1, first.operations().size());
        assertEquals(2070000, first.operations().getFirst().getItem().getItemId());
        assertFalse(first.noSpace().isEmpty());
        assertEquals(1, rocks.getQuantity()); // No topping up existing stacks.
        im.getEquipInventory().setSize(96);
        final var second = TestItemSet.giveMissing(cd);
        assertTrue(second.added() > 0);
        assertTrue(second.noSpace().isEmpty());
        assertTrue(TestItemSet.giveMissing(cd).operations().isEmpty());
        for (var item : im.getEquipInventory().getItems().values()) {
            assertEquals(WeaponType.THROWINGGLOVE, WeaponType.getByItemId(item.getItemId()));
        }
        assertEquals(originalBullets, im.getItemCount(2330000)); // Existing unrelated bullets survive.
    }

    @Test
    void intermediateFemaleJobGetsMatchingArmorAndAmmo() {
        final var cd = JobSeeder.createCharacter(1, 1, Job.BOWMASTER);
        cd.getCharacterStat().setJob(Job.HUNTER.getJobId());
        cd.getCharacterStat().setGender((byte) 1);
        final var items = TestItemSet.itemsFor(cd.getCharacterStat());
        assertTrue(items.containsKey(1041002));
        assertFalse(items.containsKey(1040002));
        assertTrue(items.containsKey(2060000));
        assertFalse(items.containsKey(2061000));
        for (int id : items.keySet()) {
            if (InventoryType.getByItemId(id) == InventoryType.EQUIP && id / 1000000 == 1 && id >= 1300000) {
                assertEquals(WeaponType.BOW, WeaponType.getByItemId(id));
            }
        }
    }
}
