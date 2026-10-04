package kinoko.server.dev;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import kinoko.database.DatabaseManager;
import kinoko.provider.ItemProvider;
import kinoko.provider.SkillProvider;
import kinoko.provider.StringProvider;
import kinoko.provider.item.ItemInfo;
import kinoko.provider.skill.SkillInfo;
import kinoko.server.ServerConfig;
import kinoko.world.GameConstants;
import kinoko.world.item.*;
import kinoko.world.job.Job;
import kinoko.world.job.JobConstants;
import kinoko.world.quest.QuestManager;
import kinoko.world.skill.SkillManager;
import kinoko.world.skill.SkillRecord;
import kinoko.world.user.Account;
import kinoko.world.user.CharacterData;
import kinoko.world.user.data.*;
import kinoko.world.user.stat.CharacterStat;
import kinoko.world.user.stat.ExtendSp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicit, offline development seed; reruns preserve existing characters and IDs. */
public final class JobSeeder {
    public static final List<Job> JOBS = List.of(
            Job.HERO, Job.PALADIN, Job.DARK_KNIGHT, Job.ARCH_MAGE_FP, Job.ARCH_MAGE_IL, Job.BISHOP,
            Job.BOWMASTER, Job.MARKSMAN, Job.NIGHT_LORD, Job.SHADOWER, Job.BLADE_MASTER,
            Job.BUCCANEER, Job.CORSAIR, Job.DAWN_WARRIOR_3, Job.BLAZE_WIZARD_3,
            Job.WIND_ARCHER_3, Job.NIGHT_WALKER_3, Job.THUNDER_BREAKER_3,
            Job.ARAN_4, Job.EVAN_10, Job.BATTLE_MAGE_4, Job.WILD_HUNTER_4, Job.MECHANIC_4);

    private JobSeeder() {
    }

    public static Map<String, Map<String, Object>> seed(Path output) throws Exception {
        final Map<String, Map<String, Object>> mapping = new LinkedHashMap<>();
        for (Job job : JOBS) {
            final int jobId = job.getJobId();
            final String username = "devjob" + jobId;
            final String name = "DevJob" + jobId;
            Optional<Account> accountResult = DatabaseManager.accountAccessor().getAccountByUsername(username);
            if (accountResult.isEmpty()) {
                require(DatabaseManager.accountAccessor().newAccount(username, "devjob"), "create account " + username);
                accountResult = DatabaseManager.accountAccessor().getAccountByUsername(username);
            }
            final Account account = accountResult.orElseThrow();
            CharacterData cd = DatabaseManager.characterAccessor().getCharacterByName(name).orElse(null);
            if (cd == null) {
                final int characterId = DatabaseManager.idAccessor().nextCharacterId().orElseThrow();
                cd = createCharacter(account.getId(), characterId, job);
                require(DatabaseManager.characterAccessor().newCharacter(cd), "create character " + name);
            }
            require(cd.getAccountId() == account.getId() && cd.getCharacterStat().getJob() == jobId,
                    "existing seed character conflicts with " + name);
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("accountId", account.getId());
            entry.put("characterId", cd.getCharacterId());
            entry.put("username", username);
            entry.put("characterName", name);
            entry.put("jobName", job.name());
            mapping.put(Integer.toString(jobId), entry);
        }
        final Path absolute = output.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        final Path temporary = Files.createTempFile(absolute.getParent(), "seed_acc-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJSONString(mapping, JSONWriter.Feature.PrettyFormat) + "\n");
            Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return mapping;
    }

    public static CharacterData createCharacter(int accountId, int characterId, Job job) {
        final CharacterData cd = new CharacterData(accountId);
        cd.setItemSnCounter(new AtomicInteger(1));
        cd.setCreationTime(Instant.now());
        final CharacterStat cs = new CharacterStat();
        cs.setId(characterId);
        cs.setName("DevJob" + job.getJobId());
        cs.setGender((byte) 0);
        cs.setFace(20000);
        cs.setHair(30000);
        cs.setLevel((short) (JobConstants.isCygnusJob(job.getJobId()) ? 120 : 200));
        cs.setJob(job.getJobId());
        cs.setSubJob((short) (JobConstants.isDualJob(job.getJobId()) ? 1 : 0));
        cs.setBaseStr((short) 500);
        cs.setBaseDex((short) 500);
        cs.setBaseInt((short) 500);
        cs.setBaseLuk((short) 500);
        cs.setMaxHp(30000);
        cs.setHp(30000);
        cs.setMaxMp(30000);
        cs.setMp(30000);
        cs.setSp(ExtendSp.from(Map.of()));
        cs.setPosMap(100000000);
        cd.setCharacterStat(cs);
        final InventoryManager im = new InventoryManager();
        im.setEquipped(new Inventory(Short.MAX_VALUE));
        im.setEquipInventory(new Inventory(GameConstants.INVENTORY_SLOT_MAX));
        im.setConsumeInventory(new Inventory(GameConstants.INVENTORY_SLOT_MAX));
        im.setInstallInventory(new Inventory(GameConstants.INVENTORY_SLOT_MAX));
        im.setEtcInventory(new Inventory(GameConstants.INVENTORY_SLOT_MAX));
        im.setCashInventory(new Inventory(GameConstants.INVENTORY_SLOT_MAX));
        im.setMoney(100000000);
        im.setExtSlotExpire(Instant.now());
        cd.setInventoryManager(im);

        // Normal male starter armor, shared by every job.
        equip(cd, BodyPart.CAP, 1002008);
        equip(cd, BodyPart.CLOTHES, 1040002);
        equip(cd, BodyPart.PANTS, 1060002);
        equip(cd, BodyPart.SHOES, 1072001);
        equip(cd, BodyPart.GLOVES, 1082002);
        equip(cd, BodyPart.CAPE, 1102000);
        final List<ItemInfo> mapleWeapons = mapleWeapons();
        final WeaponType weaponType = weaponFor(job);
        final ItemInfo weapon = mapleWeapons.stream()
                .filter(ii -> WeaponType.getByItemId(ii.getItemId()) == weaponType && canEquip(ii, cs, 0))
                .max(Comparator.comparingInt(ItemInfo::getReqLevel).thenComparingInt(ItemInfo::getItemId))
                .orElseGet(() -> findEquipment(cs, BodyPart.WEAPON, weaponType, 0));
        equip(cd, BodyPart.WEAPON, weapon.getItemId());
        if (job == Job.BLADE_MASTER) {
            equip(cd, BodyPart.SHIELD, findEquipment(cs, BodyPart.SHIELD, WeaponType.SUB_DAGGER, weapon.getItemId()).getItemId());
        }
        for (ItemInfo ii : mapleWeapons) {
            add(cd, ii, 1);
        }
        // Bow/crossbow arrows, throwing stars and gun bullets, plus skill reagents and potions.
        for (int id : new int[]{2060000, 2061000, 2070000, 2330000, 4006000, 4006001, 2000005}) {
            final ItemInfo ii = itemInfo(id);
            add(cd, ii, Math.min(ii.getSlotMax(), 2000));
        }
        final SkillManager sm = new SkillManager();
        for (int root : JobConstants.getSkillRootFromJob(job.getJobId())) {
            for (SkillInfo si : SkillProvider.getSkillsForJob(Job.getById(root))) {
                if (si.getMaxLevel() <= 0) {
                    continue;
                }
                final SkillRecord sr = new SkillRecord(si.getSkillId());
                sr.setSkillLevel(si.getMaxLevel());
                sr.setMasterLevel(si.getMaxLevel());
                sm.addSkill(sr);
            }
        }
        cd.setSkillManager(sm);
        cd.setQuestManager(new QuestManager());
        cd.setConfigManager(ConfigManager.defaults());
        cd.setPopularityRecord(new PopularityRecord());
        cd.setMiniGameRecord(new MiniGameRecord());
        cd.setCoupleRecord(CoupleRecord.from(im.getEquipped(), im.getEquipInventory()));
        cd.setMapTransferInfo(new MapTransferInfo());
        final WildHunterInfo whi = new WildHunterInfo();
        if (JobConstants.isWildHunterJob(job.getJobId())) {
            whi.setRidingType(1);
        }
        cd.setWildHunterInfo(whi);
        cd.setFriendMax(ServerConfig.FRIEND_MAX_BASE);
        return cd;
    }

    public static List<ItemInfo> mapleWeapons() {
        return ItemProvider.getItemInfos().stream()
                .filter(ii -> ItemConstants.isWeapon(ii.getItemId()) && !ii.isCash())
                .filter(ii -> Optional.ofNullable(StringProvider.getItemName(ii.getItemId())).orElse("").contains("Maple"))
                .sorted(Comparator.comparingInt(ItemInfo::getItemId)).toList();
    }

    public static WeaponType weaponFor(Job job) {
        return switch (job) {
            case HERO, DAWN_WARRIOR_3 -> WeaponType.TH_SWORD;
            case PALADIN -> WeaponType.TH_MACE;
            case DARK_KNIGHT -> WeaponType.SPEAR;
            case ARAN_4 -> WeaponType.POLEARM;
            case ARCH_MAGE_FP, ARCH_MAGE_IL, BISHOP, BLAZE_WIZARD_3, EVAN_10, BATTLE_MAGE_4 -> WeaponType.STAFF;
            case BOWMASTER, WIND_ARCHER_3 -> WeaponType.BOW;
            case MARKSMAN, WILD_HUNTER_4 -> WeaponType.CROSSBOW;
            case NIGHT_LORD, NIGHT_WALKER_3 -> WeaponType.THROWINGGLOVE;
            case SHADOWER, BLADE_MASTER -> WeaponType.DAGGER;
            case BUCCANEER, THUNDER_BREAKER_3 -> WeaponType.KNUCKLE;
            case CORSAIR, MECHANIC_4 -> WeaponType.GUN;
            default -> throw new IllegalArgumentException("Not a final playable job: " + job);
        };
    }

    private static ItemInfo findEquipment(CharacterStat cs, BodyPart part, WeaponType type, int weaponId) {
        return ItemProvider.getItemInfos().stream()
                .filter(ii -> ItemType.getByItemId(ii.getItemId()) == ItemType.EQUIP && !ii.isCash())
                .filter(ii -> BodyPart.getByItemId(ii.getItemId()).contains(part))
                .filter(ii -> WeaponType.getByItemId(ii.getItemId()) == type && canEquip(ii, cs, weaponId))
                .min(Comparator.comparingInt(ItemInfo::getReqLevel).thenComparingInt(ItemInfo::getItemId)).orElseThrow();
    }

    private static boolean canEquip(ItemInfo ii, CharacterStat cs, int weaponId) {
        return ii.isAbleToEquip(cs.getGender(), cs.getLevel(), cs.getJob(), cs.getSubJob(),
                cs.getBaseStr(), cs.getBaseDex(), cs.getBaseInt(), cs.getBaseLuk(), cs.getPop(), 1, weaponId, 0);
    }

    private static void equip(CharacterData cd, BodyPart part, int itemId) {
        final ItemInfo ii = itemInfo(itemId);
        final Item weapon = cd.getInventoryManager().getEquipped().getItem(BodyPart.WEAPON.getValue());
        require(canEquip(ii, cd.getCharacterStat(), weapon == null ? 0 : weapon.getItemId()), "equip " + itemId);
        cd.getInventoryManager().getEquipped().putItem(part.getValue(), ii.createItem(cd.getNextItemSn()));
    }

    private static void add(CharacterData cd, ItemInfo ii, int quantity) {
        require(cd.getInventoryManager().addItem(ii.createItem(cd.getNextItemSn(), quantity)).isPresent(),
                "inventory capacity for " + ii.getItemId());
    }

    private static ItemInfo itemInfo(int id) {
        return ItemProvider.getItemInfo(id).orElseThrow(() -> new IllegalStateException("Missing seed item " + id));
    }

    private static void require(boolean success, String operation) {
        if (!success) {
            throw new IllegalStateException("Could not " + operation);
        }
    }
}
