package kinoko.packet.stage;

import kinoko.server.ServerConfig;
import kinoko.server.ServerConstants;
import kinoko.server.header.OutHeader;
import kinoko.server.node.ChannelInfo;
import kinoko.server.packet.OutPacket;

public final class DevPacket {
    private DevPacket() {
    }

    /** Configured destinations for the single world hosted by this server. */
    public static OutPacket worldList() {
        if (ServerConfig.WORLD_ID < 0 || ServerConfig.WORLD_ID > 255 ||
                ServerConfig.CHANNELS_PER_WORLD < 1 || ServerConfig.CHANNELS_PER_WORLD > 256) {
            throw new IllegalStateException("Dev world/channel IDs must fit unsigned bytes");
        }
        final OutPacket packet = OutPacket.of(OutHeader.DevWorldList);
        packet.encodeByte(1); // format version
        packet.encodeShort(1); // world count
        packet.encodeByte(ServerConfig.WORLD_ID);
        packet.encodeString(ServerConfig.WORLD_NAME);
        packet.encodeShort(ServerConfig.CHANNELS_PER_WORLD);
        for (int channelId = 0; channelId < ServerConfig.CHANNELS_PER_WORLD; channelId++) {
            packet.encodeByte(channelId);
            packet.encodeString(ChannelInfo.from(channelId, 0).getName());
            packet.encodeArray(ServerConstants.SERVER_HOST);
            packet.encodeShort(ServerConstants.CHANNEL_PORT + channelId);
        }
        return packet;
    }
}
