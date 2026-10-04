package kinoko.database.json;

import com.alibaba.fastjson2.JSONObject;
import kinoko.world.skill.SkillConstants;
import kinoko.world.user.data.PopularityRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class CharacterDataSerializerTest {
    private final CharacterDataSerializer serializer = new CharacterDataSerializer();

    @ParameterizedTest
    @ValueSource(longs = {0L, 100000L, 2147483647L, 2147483648L, 1791072000123L})
    public void skillCooltimesSurviveJsonRoundTrip(long epochMillis) {
        final Map<Integer, Instant> expected = Map.of(5221006, Instant.ofEpochMilli(epochMillis));
        final JSONObject parsed = JSONObject.parseObject(serializer.serializeSkillCooltimes(expected).toJSONString());

        assertEquals(expected, serializer.deserializeSkillCooltimes(parsed));
    }

    @Test
    public void battleshipDurabilityAndActiveCooldownSurviveJsonRoundTrip() {
        final Map<Integer, Instant> expected = Map.of(
                SkillConstants.BATTLESHIP_DURABILITY, Instant.ofEpochSecond(100),
                5221006, Instant.ofEpochMilli(1791072000123L)
        );
        final JSONObject parsed = JSONObject.parseObject(serializer.serializeSkillCooltimes(expected).toJSONString());

        assertTrue(parsed.get(String.valueOf(SkillConstants.BATTLESHIP_DURABILITY)) instanceof Integer);
        assertTrue(parsed.get("5221006") instanceof Long);
        assertEquals(expected, serializer.deserializeSkillCooltimes(parsed));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 100000L, 2147483647L, 2147483648L, 1791072000123L})
    public void popularityRecordsSurviveJsonRoundTrip(long epochMillis) {
        final PopularityRecord expected = new PopularityRecord();
        expected.addRecord(123, Instant.ofEpochMilli(epochMillis));
        final JSONObject parsed = JSONObject.parseObject(serializer.serializePopularityRecord(expected).toJSONString());

        assertEquals(expected.getRecords(), serializer.deserializePopularityRecord(parsed).getRecords());
    }

    @Test
    public void emptyRecordsLoad() {
        assertTrue(serializer.deserializeSkillCooltimes(JSONObject.parseObject("{}")).isEmpty());
        assertTrue(serializer.deserializePopularityRecord(JSONObject.parseObject("{}")).getRecords().isEmpty());
    }
}
