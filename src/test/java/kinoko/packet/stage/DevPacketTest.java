package kinoko.packet.stage;

import kinoko.server.ServerConfig;
import kinoko.server.ServerConstants;
import kinoko.server.header.OutHeader;
import kinoko.server.packet.NioBufferInPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DevPacketTest {
    @Test
    void worldListCanBeDecodedWithDocumentedWireLayout() {
        final var packet = new NioBufferInPacket(DevPacket.worldList().getData());
        assertEquals(434, Short.toUnsignedInt(packet.decodeShort()));
        assertEquals(OutHeader.DevWorldList, OutHeader.getByValue((short) 434));
        assertNull(OutHeader.getByValue((short) 433));
        assertEquals(1, packet.decodeByte());
        assertEquals(1, packet.decodeShort());
        assertEquals(ServerConfig.WORLD_ID, Byte.toUnsignedInt(packet.decodeByte()));
        assertEquals(ServerConfig.WORLD_NAME, packet.decodeString());
        assertEquals(ServerConfig.CHANNELS_PER_WORLD, Short.toUnsignedInt(packet.decodeShort()));
        for (int i = 0; i < ServerConfig.CHANNELS_PER_WORLD; i++) {
            assertEquals(i, Byte.toUnsignedInt(packet.decodeByte()));
            assertEquals(ServerConfig.WORLD_NAME + " - " + (i + 1), packet.decodeString());
            assertArrayEquals(ServerConstants.SERVER_HOST, packet.decodeArray(4));
            assertEquals(ServerConstants.CHANNEL_PORT + i, Short.toUnsignedInt(packet.decodeShort()));
        }
        assertEquals(0, packet.getRemaining());
    }
}
