package kinoko.provider.item;

import kinoko.provider.wz.serialize.WzProperty;
import kinoko.provider.ProviderError;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SetItemInfoTest {
    @Test
    void preservesOptionIdsAndExplicitLevelsInTheirPieceCountTier() {
        final var optionList = new LinkedHashMap<String, Object>();
        optionList.put("1", property(Map.of("option", 30291, "level", 13)));
        optionList.put("2", property(Map.of("option", 10241, "level", 13)));
        final var bonuses = property(Map.of("incAllStat", 10, "Option", property(optionList)));
        final var set = property(Map.of("ItemID", property(Map.of("0", 1000000)),
                "Effect", property(Map.of("6", bonuses))));

        final var effects = SetItemInfo.from(set).getEffect();
        assertEquals(List.of(new SetItemInfo.ItemOption(30291, 13), new SetItemInfo.ItemOption(10241, 13)),
                effects.get(6).options());
        assertEquals(10, effects.get(6).stats().get(ItemInfoType.incSTR));
        assertEquals(1, effects.size());
        assertThrows(UnsupportedOperationException.class, () -> effects.get(6).options().clear());
    }

    @Test
    void rejectsMalformedOptionsInsteadOfSilentlyDiscardingThem() {
        final var bonuses = property(Map.of("Option", 123));
        final var set = property(Map.of("ItemID", property(Map.of("0", 1000000)),
                "Effect", property(Map.of("6", bonuses))));
        assertThrows(ProviderError.class, () -> SetItemInfo.from(set));
        assertThrows(ProviderError.class, () -> new SetItemInfo.ItemOption(30291, 0));
    }

    @Test
    void allStatAddsToIndividualBonusesRegardlessOfPropertyOrder() {
        for (boolean allStatFirst : new boolean[]{true, false}) {
            final var bonuses = new LinkedHashMap<String, Object>();
            if (allStatFirst) {
                bonuses.put("incAllStat", 4);
            }
            bonuses.put("incSTR", 3);
            if (!allStatFirst) {
                bonuses.put("incAllStat", 4);
            }
            final var effects = new LinkedHashMap<String, Object>();
            effects.put("4", property(bonuses));
            final var items = new LinkedHashMap<String, Object>();
            items.put("0", 1000000);
            final var set = new LinkedHashMap<String, Object>();
            set.put("ItemID", property(items));
            set.put("Effect", property(effects));

            final var stats = SetItemInfo.from(property(set)).getEffect().get(4).stats();
            assertEquals(7, stats.get(ItemInfoType.incSTR));
            assertEquals(4, stats.get(ItemInfoType.incDEX));
            assertEquals(4, stats.get(ItemInfoType.incINT));
            assertEquals(4, stats.get(ItemInfoType.incLUK));
        }
    }

    private static WzProperty property(Map<String, Object> items) {
        return new WzProperty(null, 0, new LinkedHashMap<>(items));
    }
}
