package kinoko.handler.stage;

import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import kinoko.server.ServerConfig;
import kinoko.server.header.InHeader;
import kinoko.server.node.ChannelServerNode;
import kinoko.server.node.Client;
import kinoko.server.packet.NioBufferInPacket;
import kinoko.server.packet.OutPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DevMigrationTest {
    private static final NioEventLoopGroup eventLoop = new NioEventLoopGroup(1);

    @AfterAll
    static void shutdownEventLoop() {
        eventLoop.shutdownGracefully(0, 1, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly();
    }

    private static NioSocketChannel newSocket() {
        final NioSocketChannel socket = new NioSocketChannel();
        eventLoop.register(socket).syncUninterruptibly();
        return socket;
    }

    @Test
    void devOpcodeIsReachableWithoutChangingNormalMigrationOrSentinel() {
        assertEquals(InHeader.DevMigrateIn, InHeader.getByValue((short) 315));
        assertEquals(InHeader.MigrateIn, InHeader.getByValue((short) 20));
        assertNull(InHeader.getByValue((short) 314));
        assertNull(InHeader.getByValue((short) 316));
        assertNull(InHeader.getByValue((short) -1));
    }

    @Test
    void rejectsTruncatedAndOversizedBodiesBeforeLoadingDatabase() {
        for (int length : new int[]{0, 17, 19, 20, 21, 23}) {
            final NioSocketChannel socket = newSocket();
            final Client client = new Client(new ChannelServerNode(0, 8585), socket);
            MigrationHandler.handleDevMigrateIn(client, new NioBufferInPacket(new byte[length]));
            socket.closeFuture().syncUninterruptibly();
            assertFalse(socket.isOpen());
            assertNull(client.getAccount());
            assertNull(client.getUser());
        }
    }

    @Test
    void rejectsWrongDestinationBeforeLoadingDatabase() {
        assumeTrue(ServerConfig.DEV_LOGIN, "Run with DEV_LOGIN=true to test destination validation");
        for (boolean wrongWorld : new boolean[]{true, false}) {
            final OutPacket body = OutPacket.of();
            body.encodeInt(1);
            body.encodeInt(2);
            body.encodeByte(wrongWorld ? (ServerConfig.WORLD_ID + 1) % 256 : ServerConfig.WORLD_ID);
            body.encodeByte(wrongWorld ? 0 : 1);
            body.encodeArray(new byte[8]);
            final NioSocketChannel socket = newSocket();
            final Client client = new Client(new ChannelServerNode(0, 8585), socket);
            MigrationHandler.handleDevMigrateIn(client, new NioBufferInPacket(body.getData()));
            socket.closeFuture().syncUninterruptibly();
            assertFalse(socket.isOpen());
            assertNull(client.getClientKey());
        }
    }

    @Test
    void rejectsNegativeSpawnMapBeforeLoadingDatabase() {
        assumeTrue(ServerConfig.DEV_LOGIN, "Run with DEV_LOGIN=true");
        final OutPacket body = OutPacket.of();
        body.encodeInt(1);
        body.encodeInt(2);
        body.encodeByte(ServerConfig.WORLD_ID);
        body.encodeByte(0);
        body.encodeArray(new byte[8]);
        body.encodeInt(-1);
        final NioSocketChannel socket = newSocket();
        final Client client = new Client(new ChannelServerNode(0, 8585), socket);
        MigrationHandler.handleDevMigrateIn(client, new NioBufferInPacket(body.getData()));
        socket.closeFuture().syncUninterruptibly();
        assertFalse(socket.isOpen());
        assertNull(client.getAccount());
    }

    @Test
    void disabledDevLoginRejectsValidBody() {
        assumeTrue(!ServerConfig.DEV_LOGIN, "Test default configuration");
        final NioSocketChannel socket = newSocket();
        final Client client = new Client(new ChannelServerNode(0, 8585), socket);
        MigrationHandler.handleDevMigrateIn(client, new NioBufferInPacket(new byte[18]));
        socket.closeFuture().syncUninterruptibly();
        assertFalse(socket.isOpen());
        assertNull(client.getClientKey());
    }
}
