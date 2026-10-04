package kinoko.server.dev;

import kinoko.provider.ItemProvider;
import kinoko.provider.item.ItemInfo;
import kinoko.world.item.*;
import kinoko.world.job.Job;
import kinoko.world.job.JobConstants;
import kinoko.world.user.CharacterData;
import kinoko.world.user.stat.CharacterStat;

import java.util.*;

public final class TestItemSet {
    private TestItemSet() {
    }

    public record Result(List<InventoryOperation> operations, int added, int present, List<Integer> noSpace) {
    }

    /** Adds absent item IDs only; equipped items count as present too. */
    public static Result giveMissing(CharacterData cd) {
        final Map<Integer, Integer> items = itemsFor(cd.getCharacterStat());
        final InventoryManager im = cd.getInventoryManager();
        final List<InventoryOperation> operations = new ArrayList<>();
        final List<Integer> noSpace = new ArrayList<>();
        int added = 0;
        int present = 0;
        for (var entry : items.entrySet()) {
            final int itemId = entry.getKey();
            if (im.getItemCount(itemId) > 0 || im.hasEquipped(itemId)) {
                present++;
                continue;
            }
            final ItemInfo ii = ItemProvider.getItemInfo(itemId).orElseThrow();
            if (!im.canAddItem(itemId, entry.getValue())) {
                noSpace.add(itemId);
                continue;
            }
            final var result = im.addItem(ii.createItem(cd.getNextItemSn(), entry.getValue()));
            if (result.isEmpty()) {
                noSpace.add(itemId);
                continue;
            }
            operations.addAll(result.get());
            added++;
        }
        return new Result(List.copyOf(operations), added, present, List.copyOf(noSpace));
    }

    public static Map<Integer, Integer> itemsFor(CharacterStat cs) {
        final int jobId = cs.getJob();
        if (Job.getById(jobId) == null || JobConstants.isAdminJob(jobId) || JobConstants.isManagerJob(jobId) || jobId == 9000) {
            throw new IllegalArgumentException("No test set for job " + jobId);
        }
        final Map<Integer, Integer> items = new LinkedHashMap<>();
        // Match gender, but leave level/stat requirements to normal equipping.
        final int[] armor = cs.getGender() == 1
                ? new int[]{1002008, 1041002, 1061002, 1072001, 1082002, 1102000}
                : new int[]{1002008, 1040002, 1060002, 1072001, 1082002, 1102000};
        for (int id : armor) {
            put(items, id, 1);
        }
        final Set<WeaponType> types = weaponTypes(jobId);
        for (ItemInfo ii : JobSeeder.mapleWeapons()) {
            if (types.contains(WeaponType.getByItemId(ii.getItemId())) && matchesJob(ii, cs)) {
                put(items, ii.getItemId(), 1);
            }
        }
        if (JobConstants.isDualJob(jobId)) {
            final ItemInfo katara = ItemProvider.getItemInfos().stream()
                    .filter(ii -> !ii.isCash() && WeaponType.getByItemId(ii.getItemId()) == WeaponType.SUB_DAGGER)
                    .filter(ii -> matchesJob(ii, cs))
                    .min(Comparator.comparingInt(ItemInfo::getReqLevel).thenComparingInt(ItemInfo::getItemId)).orElseThrow();
            put(items, katara.getItemId(), 1);
        }
        if (types.contains(WeaponType.BOW)) putStack(items, 2060000);
        if (types.contains(WeaponType.CROSSBOW)) putStack(items, 2061000);
        if (types.contains(WeaponType.THROWINGGLOVE)) putStack(items, 2070000);
        if (types.contains(WeaponType.GUN)) putStack(items, 2330000);
        for (int id : new int[]{4006000, 4006001, 2000005}) putStack(items, id);
        return items;
    }

    private static boolean matchesJob(ItemInfo ii, CharacterStat cs) {
        // Use generous requirements to include test weapons for later levels too.
        return ii.isAbleToEquip(cs.getGender(), 200, cs.getJob(), cs.getSubJob(),
                999, 999, 999, 999, 999, 1, 1332000, 0);
    }

    private static void putStack(Map<Integer, Integer> items, int id) {
        put(items, id, Math.min(2000, ItemProvider.getItemInfo(id).orElseThrow().getSlotMax()));
    }

    private static void put(Map<Integer, Integer> items, int id, int quantity) {
        ItemProvider.getItemInfo(id).orElseThrow(() -> new IllegalStateException("Missing test-set item " + id));
        items.put(id, quantity);
    }

    private static Set<WeaponType> weaponTypes(int jobId) {
        if (JobConstants.isBeginnerJob(jobId)) return Set.of(WeaponType.OH_SWORD);
        if (JobConstants.isAranJob(jobId)) return Set.of(WeaponType.POLEARM);
        if (JobConstants.isBattleMageJob(jobId)) return Set.of(WeaponType.STAFF);
        if (JobConstants.isWindArcherJob(jobId)) return Set.of(WeaponType.BOW);
        if (JobConstants.isNightWalkerJob(jobId)) return Set.of(WeaponType.THROWINGGLOVE);
        if (jobId / 100 == 11) return Set.of(WeaponType.OH_SWORD, WeaponType.TH_SWORD);
        if (jobId / 100 == 15) return Set.of(WeaponType.KNUCKLE);
        if (JobConstants.isDualJob(jobId)) return Set.of(WeaponType.DAGGER, WeaponType.SUB_DAGGER);
        if (JobConstants.isHeroJob(jobId)) return Set.of(WeaponType.OH_SWORD, WeaponType.TH_SWORD, WeaponType.OH_AXE, WeaponType.TH_AXE);
        if (JobConstants.isPaladinJob(jobId)) return Set.of(WeaponType.OH_SWORD, WeaponType.TH_SWORD, WeaponType.OH_MACE, WeaponType.TH_MACE);
        if (JobConstants.isDarkKnightJob(jobId)) return Set.of(WeaponType.SPEAR, WeaponType.POLEARM);
        if (JobConstants.isNightLordJob(jobId)) return Set.of(WeaponType.THROWINGGLOVE);
        if (JobConstants.isShadowerJob(jobId)) return Set.of(WeaponType.DAGGER);
        if (JobConstants.isBowmasterJob(jobId)) return Set.of(WeaponType.BOW);
        if (JobConstants.isMarksmanJob(jobId)) return Set.of(WeaponType.CROSSBOW);
        if (JobConstants.isBuccaneerJob(jobId)) return Set.of(WeaponType.KNUCKLE);
        if (JobConstants.isCorsairJob(jobId)) return Set.of(WeaponType.GUN);
        if (JobConstants.isWildHunterJob(jobId)) return Set.of(WeaponType.CROSSBOW);
        if (JobConstants.isMechanicJob(jobId)) return Set.of(WeaponType.GUN);
        return switch (JobConstants.getJobCategory(jobId)) {
            case 1 -> Set.of(WeaponType.OH_SWORD, WeaponType.TH_SWORD, WeaponType.OH_AXE, WeaponType.TH_AXE,
                    WeaponType.OH_MACE, WeaponType.TH_MACE, WeaponType.SPEAR, WeaponType.POLEARM);
            case 2 -> Set.of(WeaponType.WAND, WeaponType.STAFF);
            case 3 -> Set.of(WeaponType.BOW, WeaponType.CROSSBOW);
            case 4 -> Set.of(WeaponType.DAGGER, WeaponType.THROWINGGLOVE);
            case 5 -> Set.of(WeaponType.KNUCKLE, WeaponType.GUN);
            default -> throw new IllegalArgumentException("No test set for job " + jobId);
        };
    }
}
