## Kinoko

Kinoko is a server emulator for the popular mushroom game.

## Setup

Basic configuration is available via environment variables - the names and default values of the configurable options
are defined in [ServerConstants.java](src/main/java/kinoko/server/ServerConstants.java)
and [ServerConfig.java](src/main/java/kinoko/server/ServerConfig.java).

> [!NOTE]
> Client WZ files are expected to be present in the `wz/` directory in order for the provider classes to extract the
> required data. The required files are as follows:
> ```
> Character.wz
> Item.wz
> Skill.wz
> Morph.wz
> Map.wz
> Mob.wz
> Npc.wz
> Reactor.wz
> Quest.wz
> String.wz
> Etc.wz
> ```

#### Java setup

Building the project requires Java 21 and maven.

```bash
# Build jar
$ mvn clean package
```

With [just](https://github.com/casey/just) installed, launch options are:

```bash
just run            # Build and launch with normal login and encrypted traffic
just run-dev        # Build and launch with direct channel dev login
just run-plain      # Build and launch with normal login and plain traffic
just run-dev-plain  # Build and launch with direct channel dev login and plain traffic
just run-jar        # Launch the existing jar using current environment settings
just build          # Build without running tests
just test           # Run tests
```

Other server settings remain configurable through environment variables, for example:

```bash
WORLD_ID=0 CHANNEL_COUNT=1 just run-dev-plain
DEV_LOGIN=true just test
```

#### Database setup

Kinoko defaults to using SQLite as the database provider for ease of use. No setup is required in this case.

It is possible to use either CassandraDB or ScyllaDB instead of SQLite, which requires starting the database using the commands below.

```bash
# Start CassandraDB
$ docker run -d -p 9042:9042 cassandra:5.0.0

# Alternatively, start ScyllaDB
$ docker run -d -p 9042:9042 scylladb/scylla --smp 1
```

You can use [Docker Desktop](https://www.docker.com/products/docker-desktop/) or WSL on Windows.

#### Docker setup

Alternatively, docker can be used to build and start the server using the [docker-compose.yml](docker-compose.yml) file.

```bash
# Build and start containers
$ docker compose up -d
```


To start the server with CassandraDB, the [docker-compose.cassandra.yml](docker-compose.cassandra.yml) file can be used as follows:

```bash
$ docker compose -f docker-compose.cassandra.yml up -d
```

## Direct channel login for client development

Start the server with `DEV_LOGIN=true`, then connect directly to a channel port
(default `8585` for channel ID `0`). Startup prints the first stored character
(lowest character ID), its account ID, and the world/channel IDs and ports.
Accounts and characters must already exist, and the character must belong to the
supplied account. The normal central server still runs; the login server and
migration ticket are bypassed.

After reading the usual connection handshake, send `DevMigrateIn` (`315`, `0x013B`)
using the normal packet framing/encryption (or `PLAIN_TRAFFIC=true` for the existing
plain framing). All integers are little-endian:

| Field | Size |
| --- | --- |
| Opcode `0x013B` | 2 bytes |
| Account ID | 4 bytes |
| Character ID | 4 bytes |
| World ID | 1 byte |
| Channel ID (zero-based) | 1 byte |
| Client key (any 8 bytes, including zeros) | 8 bytes |
| Optional spawn map ID | 4 bytes, omit to use saved map |

The body is exactly 18 bytes, or 22 bytes when a spawn map is supplied. For example, account `1`, character `2`, world `0`,
channel `0`, and a zero client key produce this 20-byte packet before framing:

```text
3B 01 01 00 00 00 02 00 00 00 00 00 00 00 00 00 00 00 00 00
```

World/channel IDs must match the destination server. The server assigns a zero
machine ID and stores your client key for subsequent normal channel transfers.
Successful login sends `DevWorldList` before `SetField`, followed by the usual character/map initialization packets. Invalid
requests close the connection. This password-free dev login is disabled by default.

The optional spawn map must exist in the destination channel; invalid maps close
the connection. Entry uses portal `0` (the normal portal fallback applies). The
override becomes the character's current map and is saved by normal persistence.

### Client world/channel initialization

Handle custom **send opcode `434` (`0x01B2`), `DevWorldList`**, on the channel
connection. This packet is sent only after successful `DevMigrateIn`, before the
first `SetField`. Populate your client world/channel cache before entering the
field; do not route this packet through `CLogin::OnPacket` or show world select.
It describes the configured single world and all its channels, without live
population counts. A failed login sends no list. Normal login/migration is unchanged.

All integers use little-endian encoding. `string` means a `u16` byte length followed
by that many US-ASCII bytes, using the server's normal packet string encoding.

| Field | Wire type |
| --- | --- |
| Opcode | `u16 = 434` |
| Format version | `u8 = 1` |
| World count | `u16` (currently 1) |
| Per world: world ID | `u8` |
| World name | `string` |
| Channel count | `u16` |
| Per channel: zero-based channel ID | `u8` |
| Channel name (e.g. `Kinoko - 1`) | `string` |
| Channel IPv4 address | 4 raw bytes, network address order |
| Channel port | `u16` |

Use `WORLD_ID`, `WORLD_NAME`, `CHANNEL_COUNT`, and `SERVER_HOST` to configure the
advertised list. Display channel IDs as `id + 1`; keep zero-based IDs for protocol
requests. Reject unsupported format versions in the client. Subsequent channel
transfers use the normal migration flow, retaining the list in client context.

### Final-job testing accounts

With the server stopped, run:

```sh
just seed-jobs                 # builds, seeds the configured DB, writes seed_acc.json
just run-dev-plain             # or just run-dev for encrypted traffic
```

`just seed-jobs /path/to/seed_acc.json` selects another output location.
The equivalent jar command is `java -jar target/server.jar --seed-jobs [output]`.
The command loads the local WZ providers and exits without starting server nodes.
It creates 23 accounts, one for each final playable v95 job: 13 Explorer paths
(including Dual Blade), five Cygnus paths, Aran, Evan, and three Resistance paths.
Intermediate jobs, beginners, and GM jobs are excluded.

`seed_acc.json` is a direct mapping from decimal job ID strings to records:

```json
{
  "112": {
    "accountId": 1,
    "characterId": 1,
    "username": "devjob112",
    "characterName": "DevJob112",
    "jobName": "HERO"
  }
}
```

IDs above are illustrative; use the generated IDs for your database. For a client
launch option such as `--job 112`, read `seed_acc.json["112"]`, put `accountId` and
`characterId` into `DevMigrateIn`, and use your selected world/channel. Append a map
ID when a spawn override is requested. Password for newly created accounts is
`devjob`; direct login does not use it. Rerunning preserves IDs and character
progress/inventories and regenerates the mapping; it does not reset characters.
Reserved `devjob<ID>` accounts and `DevJob<ID>` names must belong to the matching job.

Each new character starts in Henesys (`100000000`) at level 200 (Cygnus 120), with
500 in each base stat, 30,000 HP/MP, 100 million mesos, and 96 slots per inventory.
It has normal starter armor equipped and a compatible Maple weapon when available
(otherwise a compatible WZ weapon); Dual Blade also has a katara equipped. Wild
Hunter starts with a jaguar selected. Every non-cash weapon whose local WZ name
contains `Maple` is in the equip inventory, including weapons for other classes.
Skills from the character's own job advancement line, including beginner skills,
are set to their WZ maximum with full mastery. Other classes' skills are excluded.
Every character receives bow arrows, crossbow arrows, throwing stars, gun bullets,
summoning rocks (`4006000`), magic rocks (`4006001`), and Power Elixirs; each stack
contains up to 2,000 items, capped by its WZ stack limit. Seeding fails explicitly
if required gear is missing or the complete Maple weapon collection will not fit.

Focused validation (the second command also reads local WZ files):

```sh
mvn -Dtest=DevPacketTest,DevMigrationTest,FirstCharacterInfoTest,JobSeederTest,TestItemSetTest test
DEV_LOGIN=true TEST_JOB_SEED_WZ=true mvn -Dtest=DevPacketTest,DevMigrationTest,FirstCharacterInfoTest,JobSeederTest,TestItemSetTest test
```

### Give missing test items in game

Run `!test-set` (alias `!testset`; uses `COMMAND_PREFIX`) to add starter armor,
Maple weapons suitable for your current job, relevant arrows/stars/bullets,
summoning rocks, magic rocks, and Power Elixirs to your inventory. Dual Blade also
gets a starter katara. Intermediate jobs and beginners are supported too. Equipment
still uses its normal level/stat requirements when you equip it.

Each item ID is added only if absent from both inventory and equipped slots.
Existing stacks are preserved, including partly used stacks; this command does
not refill them. Items are placed in inventory without changing equipped gear,
stats, skills, or inventory sizes. If space runs out, the command reports it;
free slots and run it again to receive the remaining missing items. Access follows
the existing in-game command system.
