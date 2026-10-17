package kinoko.provider.item;

import kinoko.provider.ProviderError;
import kinoko.provider.WzProvider;
import kinoko.provider.wz.serialize.WzProperty;

import java.util.*;

public final class SetItemInfo {
    private final Set<Integer> items; // itemIds
    private final Map<Integer, Effect> effect; // required piece count -> bonuses

    public record ItemOption(int id, int level) {
        public ItemOption {
            if (id <= 0 || level <= 0) {
                throw new ProviderError("Invalid set item option ID %d or level %d", id, level);
            }
        }
    }

    public record Effect(Map<ItemInfoType, Integer> stats, List<ItemOption> options) {
        public Effect {
            stats = Map.copyOf(stats);
            options = List.copyOf(options);
        }
    }

    public SetItemInfo(Set<Integer> items, Map<Integer, Effect> effect) {
        this.items = items;
        this.effect = effect;
    }

    public Set<Integer> getItems() {
        return items;
    }

    public Map<Integer, Effect> getEffect() {
        return effect;
    }

    public static SetItemInfo from(WzProperty setItemProp) throws ProviderError {
        // Items
        final Set<Integer> items = new HashSet<>();
        if (!(setItemProp.get("ItemID") instanceof WzProperty itemIdList)) {
            throw new ProviderError("Could not resolve set item id list");
        }
        for (var itemIdEntry : itemIdList.getItems().entrySet()) {
            final int itemId = WzProvider.getInteger(itemIdEntry.getValue());
            items.add(itemId);
        }
        // Effect
        final Map<Integer, Effect> effect = new HashMap<>();
        if (!(setItemProp.get("Effect") instanceof WzProperty effectList)) {
            throw new ProviderError("Could not resolve set item effect list");
        }
        for (var effectEntry : effectList.getItems().entrySet()) {
            final int count = WzProvider.getInteger(effectEntry.getKey());
            if (!(effectEntry.getValue() instanceof WzProperty effectProp)) {
                throw new ProviderError("Could not resolve set item effect prop");
            }
            final Map<ItemInfoType, Integer> stats = new EnumMap<>(ItemInfoType.class);
            final List<ItemOption> options = new ArrayList<>();
            for (var statEntry : effectProp.getItems().entrySet()) {
                if (statEntry.getKey().equals("incAllStat")) {
                    final int value = WzProvider.getInteger(statEntry.getValue());
                    for (ItemInfoType type : List.of(ItemInfoType.incSTR, ItemInfoType.incDEX, ItemInfoType.incINT, ItemInfoType.incLUK)) {
                        stats.merge(type, value, Integer::sum);
                    }
                    continue;
                }
                if (statEntry.getKey().equals("Option")) {
                    if (!(statEntry.getValue() instanceof WzProperty optionList)) {
                        throw new ProviderError("Could not resolve set item option list for piece count %d", count);
                    }
                    for (var optionEntry : optionList.getItems().entrySet()) {
                        if (!(optionEntry.getValue() instanceof WzProperty optionProp)) {
                            throw new ProviderError("Could not resolve set item option %s", optionEntry.getKey());
                        }
                        options.add(new ItemOption(
                                WzProvider.getInteger(optionProp.get("option")),
                                WzProvider.getInteger(optionProp.get("level"))
                        ));
                    }
                    continue;
                }
                if (ItemInfoType.isIgnored(statEntry.getKey())) {
                    throw new ProviderError("Unhandled set item effect stat : %s", statEntry.getKey());
                }
                final ItemInfoType type = ItemInfoType.fromName(statEntry.getKey());
                if (type == ItemInfoType.setKey) {
                    // Visitor set property
                    continue;
                }
                final int value = WzProvider.getInteger(statEntry.getValue());
                stats.merge(type, value, Integer::sum);
            }
            effect.put(count, new Effect(stats, options));
        }
        return new SetItemInfo(
                Collections.unmodifiableSet(items),
                Collections.unmodifiableMap(effect)
        );
    }
}
