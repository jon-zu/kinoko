package kinoko.server.dev;

import kinoko.provider.ItemProvider;
import kinoko.provider.SkillProvider;
import kinoko.provider.StringProvider;
import kinoko.world.item.BodyPart;
import kinoko.world.item.WeaponType;
import kinoko.world.job.Job;
import kinoko.world.job.JobConstants;
import kinoko.server.packet.OutPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JobSeederTest {
    @Test
    void finalJobsHaveOnlyTheirOwnAdvancementRoots() {
        assertEquals(23, new HashSet<>(JobSeeder.JOBS).size());
        assertTrue(JobConstants.getSkillRootFromJob(434).containsAll(Set.of(0, 400, 430, 431, 432, 433, 434)));
        assertFalse(JobConstants.getSkillRootFromJob(434).contains(410));
        assertFalse(JobConstants.getSkillRootFromJob(112).contains(120));
        for (Job job : JobSeeder.JOBS) {
            assertNotEquals(WeaponType.NONE, JobSeeder.weaponFor(job));
            assertTrue(JobConstants.getSkillRootFromJob(job.getJobId()).contains((int) job.getJobId()));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_JOB_SEED_WZ", matches = "true")
    void allFinalJobsAreEquippedSuppliedAndMaxedFromLocalWz() {
        ItemProvider.initialize();
        SkillProvider.initialize();
        StringProvider.initialize();
        assertFalse(JobSeeder.mapleWeapons().isEmpty());
        int characterId = 1;
        for (Job job : JobSeeder.JOBS) {
            final var cd = JobSeeder.createCharacter(1, characterId++, job);
            final var im = cd.getInventoryManager();
            final var weapon = im.getEquipped().getItem(BodyPart.WEAPON.getValue());
            assertEquals(JobSeeder.weaponFor(job), WeaponType.getByItemId(weapon.getItemId()), job.name());
            if (job == Job.BLADE_MASTER) {
                assertEquals(WeaponType.SUB_DAGGER, WeaponType.getByItemId(im.getEquipped().getItem(10).getItemId()));
            }
            for (var ii : JobSeeder.mapleWeapons()) {
                assertEquals(1, im.getItemCount(ii.getItemId()), "Maple weapon " + ii.getItemId());
            }
            for (int id : new int[]{2060000, 2061000, 2070000, 2330000, 4006000, 4006001, 2000005}) {
                assertEquals(Math.min(2000, ItemProvider.getItemInfo(id).orElseThrow().getSlotMax()), im.getItemCount(id));
            }
            final Set<Integer> expectedSkills = new HashSet<>();
            for (int root : JobConstants.getSkillRootFromJob(job.getJobId())) {
                for (var si : SkillProvider.getSkillsForJob(Job.getById(root))) {
                    if (si.getMaxLevel() <= 0) continue;
                    expectedSkills.add(si.getSkillId());
                    final var sr = cd.getSkillManager().getSkill(si.getSkillId()).orElseThrow();
                    assertEquals(si.getMaxLevel(), sr.getSkillLevel());
                    assertEquals(si.getMaxLevel(), sr.getMasterLevel());
                }
            }
            assertFalse(expectedSkills.isEmpty());
            assertEquals(expectedSkills.size(), cd.getSkillManager().getSkillRecords().size());
            final var packet = OutPacket.of();
            assertDoesNotThrow(() -> cd.encode(packet));
            assertTrue(packet.getSize() > 0);
        }
    }
}
