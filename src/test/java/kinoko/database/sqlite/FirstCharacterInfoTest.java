package kinoko.database.sqlite;

import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static kinoko.database.schema.CharacterDataSchema.*;
import static org.junit.jupiter.api.Assertions.*;

class FirstCharacterInfoTest {
    @Test
    void findsLowestCharacterIdAndHandlesEmptyDatabase() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE character_table (" + ACCOUNT_ID + " INTEGER, " +
                    CHARACTER_ID + " INTEGER, " + CHARACTER_NAME + " TEXT)");
            final var accessor = new SqliteCharacterAccessor(connection);
            assertTrue(accessor.getFirstCharacterInfo().isEmpty());
            statement.execute("INSERT INTO character_table VALUES (7, 20, 'Later'), (3, 2, 'First')");
            final var first = accessor.getFirstCharacterInfo().orElseThrow();
            assertEquals(3, first.getAccountId());
            assertEquals(2, first.getCharacterId());
            assertEquals("First", first.getCharacterName());
        }
    }
}
