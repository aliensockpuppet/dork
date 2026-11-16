# Dork

The plugin suite from DorkMC, a cracked (offline-mode) anarchy PvP server.
Small plugins for Paper and Purpur, each doing one simple job that players want:
homes, TPA, spawn, RTP, a menu, stats. Each one is built to be sealed first:
every command is rate-limited, every teleport is gated, and the common anarchy
exploits are closed by default.

It was made for PvP anarchy, but every rule can be changed in config, and it runs
on a normal survival server too.

- Paper or Purpur 1.21.11, Java 21 or newer
- One required plugin (DorkCore). Install only the other plugins you want.
- No hard-coded links. MIT license (DorkCombat is CC BY-NC-SA 4.0, see License).

## Best with AnarchyExploitFixes and LPX

Dork seals its own commands, teleports and spawn. It does not patch vanilla
game exploits or packet crashes. On DorkMC it runs next to two plugins that do,
and that is the setup it is tuned for:

- [AnarchyExploitFixes](https://modrinth.com/plugin/anarchyexploitfixes): patches
  vanilla anarchy exploits, lag machines and illegal items.
- [LPX AntiPacketExploit](https://builtbybit.com/resources/lpx-antipacketexploit.15709/):
  stops packet exploits and crash packets. The free open-source
  [OpenLPX](https://github.com/dkbfk/OpenLPX) covers the same ground.

## Download

The jars are in the [releases](releases) folder. Get `dorkcore-1.0.0.jar` plus any
other plugin you want. `dorkmotd-velocity-1.0.0.jar` goes on a Velocity proxy, the
rest go on the Paper or Purpur server.

## The plugins

| Plugin | Needs | What it does |
|---|---|---|
| DorkCore | nothing | Required. Spawn, homes, TPA, `/back`, private messages, ignore, player toggles, chat anti-spam. Also the shared cooldown, message, sound and combat-tag services that the other plugins use. |
| DorkCombat | DorkCore | Combat tagging. A tagged player cannot teleport, and dies if they log out. |
| DorkStats | DorkCore | Kills, deaths, blocks placed and broken, playtime. Leaderboards and PlaceholderAPI placeholders. |
| RTPQueue | DorkCore | `/rtp` to a safe random spot. A PvP matchmaking queue with fair gear brackets. Random respawn for players with no bed. |
| DorkMenu | DorkCore | One menu for everything, made with the native dialog screens. Also adds a button to the ESC screen. |
| SpawnPlatform | DorkCore | A bedrock floor at spawn, liquid protection, and an optional timed clear. |
| DorkRestart | DorkCore | One daily restart, with warnings, that clears combat tags first. |
| DorkSurvey | DorkCore | A short button survey with a one-time reward. |
| DorkMotd + DorkMotd-Velocity | DorkCore, Velocity | Lets players set line 2 of the server-list MOTD. Needs a Velocity proxy. |

Optional plugins that Dork works with: AuthMe (login gate), PlaceholderAPI
(stats placeholders), LuckPerms (permissions and home caps).

## Sealed against exploits

These protections are on by default.

### Spam and timing
- Every player command has a cooldown, even a command that fails or only reads.
  This stops command floods and database-write spam.
- Every cooldown is random. A player cannot learn the exact wait and time a
  command for the instant it comes off cooldown.
- Cooldowns survive a relog. Logging out and back in does not reset them.
- Chat anti-spam. A random rate limit, and a block on repeating the same line
  (case, spaces and symbols are ignored when lines are compared).

### Teleports and combat
- Combat tag blocks every teleport: spawn, home, back, TPA, RTP and the queue.
  RTP checks the tag again just before landing, so a player hit during the search
  is not carried out of the fight.
- Combat log = death. A tagged player who logs out dies, and the kill goes to
  their opponent. A tagged player who dies some other way (lava, `/suicide`) also
  gives the kill to their opponent.
- Explosion PvP is tagged: end crystals, respawn anchors and TNT minecarts.
- Ender pearls refresh a tag, so a player cannot pearl away and wait it out.
- `/back` is off by default, so a player cannot rush back to their killer or
  their loot.
- Fair matches stay fair. The queue checks gear again just before the drop.
  A player who grabs better gear after joining is taken out of the match.
- Random respawn. A player with no bed or anchor respawns at a random safe
  spot, not at spawn, which stops spawn camping.

### Information leaks
- Login gate. With AuthMe installed, a player who has not logged in cannot use
  location commands, so their coordinates never leak. If AuthMe breaks, the gate
  fails closed. Without AuthMe, the gate is off.
- TPA refusals say nothing. A refused request reads the same whether the
  target is offline, not logged in, or has requests turned off.
- No text injection. Private messages, anonymous messages and MOTD lines are
  plain text. Click, hover and insert tags are stripped.

### Spawn and world
- No liquid griefing at spawn. Buckets, dispensers, flowing liquid, melting
  ice and broken ice are blocked in the spawn area at every height.
- Safe spawn landing. `/spawn` clears a small air pocket where the player
  lands, so a box or a block at spawn cannot trap them.
- Safe RTP. A landing is never in lava, water or a blocked biome. The search
  runs off the main thread, and a player can run only one search at a time.

### Rewards and abuse
- The survey reward cannot be farmed with alts. One reward per player and per
  IP, and a minimum playtime first. Only a salted hash of the IP is stored, never
  the IP itself.
- Menu buttons cannot be hijacked. The menu runs commands by their full plugin
  name (`dorkcore:spawn`, `rtpqueue:rtp`), so another plugin that uses the same
  command name cannot take the button.
- Anonymous messages are logged to the console with the real sender, for
  moderators.

## Features and commands

Permissions marked *everyone* are on by default. *op* means only operators have it
until you grant it.

### DorkCore

| Command | What it does | Permission |
|---|---|---|
| `/spawn` | Teleport to spawn | `dorkcore.spawn` (everyone) |
| `/setspawn` | Set spawn where you stand | `dorkcore.setspawn` (op) |
| `/home [name]`, `/sethome [name]`, `/delhome <name>`, `/homes` | Homes | `dorkcore.home` (everyone) |
| `/tpa <player>`, `/tpahere <player>` | Send a teleport request | `dorkcore.tpa` (everyone) |
| `/tpaccept`, `/tpdeny`, `/tpcancel` | Answer or cancel a request | `dorkcore.tpa` (everyone) |
| `/back` | Return to your last spot (off by default, see config) | `dorkcore.back` (everyone) |
| `/msg <player> <text>`, `/r <text>` | Private messages | `dorkcore.msg` (everyone) |
| `/anonmsg <player> <text>` | A private message with no name | `dorkcore.anonmsg` (everyone) |
| `/ignore`, `/unignore`, `/ignorelist` | Hide a player's private messages | `dorkcore.ignore` (everyone) |
| `/toggle [name]` | List or flip your settings | `dorkcore.toggle` (everyone) |
| `/suicide` | Kill yourself | `dorkcore.suicide` (everyone) |

Other permissions: `dorkcore.chat.bypass` (op) skips the chat checks.
`dorkcore.homes.unlimited` (op) removes the home cap. `dorkcore.homes.<n>` sets a
cap of `n` homes.

Teleports are instant: no warmup, no countdown. The combat tag and the
cooldown are the gate.

Player toggles (`/toggle <name>`, or Settings in the menu): `tparequests`
(short name `tp`), `tpauto`, `chat`, `pms`, `joins`, `deaths`, `queuealerts`,
`phantoms`, `style`.

Message style. Each player picks Modern (action bars, titles and sounds)
or Legacy (plain chat lines, no sounds). Every message in every plugin follows
this choice.

### DorkCombat

A hit tags both players for 15 seconds (configurable). `dorkcombat.bypass` (no one
by default) is never tagged.

### DorkStats

Tracks kills, deaths, blocks placed, blocks broken and playtime. `/dorkstats` is
the admin command (`dorkstats.admin`, op).

PlaceholderAPI placeholders:
- `%dorkstats_kills%`, `%dorkstats_deaths%`, `%dorkstats_placed%`, `%dorkstats_broken%`, `%dorkstats_kdr%`
- `%dorkstats_playtime%` (like `3d 4h`), `%dorkstats_playtime_raw%` (seconds)
- `%dorkstats_top_<stat>_name_<n>%` and `%dorkstats_top_<stat>_value_<n>%`, for example `%dorkstats_top_kills_name_1%`

### RTPQueue

| Command | What it does | Permission |
|---|---|---|
| `/rtp` | Random teleport to a safe spot | `rtpqueue.rtp` (everyone) |
| `/rtpqueue <mode>`, `/rtpqueue leave` | Join or leave a match queue | `rtpqueue.use` (everyone) |
| `/rtpqueue-admin` | Admin tools | `rtpqueue.admin` (op) |

The queue matches two players and drops them near each other at a random safe
spot. fair mode matches players by gear (netherite, diamond, lower), and by
whether they carry crystals, anchors or an elytra. open mode matches anyone.

### DorkMenu

`/dork` (or `/menu`) opens the menu: Spawn, Homes, RTP, RTPQueue, Stats, Map,
Store, Discord, Settings, Suicide. The same grid is on the ESC screen. Buttons for
plugins you did not install are hidden. `dorkmenu.use` (everyone), `dorkmenu.admin`
(op, for `/dork reload`). Shortcuts: `/stats`, `/discord`, `/store`.

The command names are set in config (`command.name`, `command.aliases`), in case
another plugin already uses them.

### SpawnPlatform

A bedrock floor at spawn and liquid protection around it. The timed clear sets
every block above the floor to air, out to a radius, up to build height. The
clear is off by default. Read the config, then turn it on.
`/spawnplatform rebuild | clear | setcenter | info | reload` (`spawnplatform.admin`, op).

### DorkRestart

One restart a day at a set time and time zone, with chat and action-bar warnings.
Combat tags are cleared before the stop, so no one dies to the restart.
`/dorkrestart` (`dorkrestart.admin`, op). Your host must start the server again
after it stops.

### DorkSurvey

`/survey` opens a short survey with buttons. Questions, answers and the reward are
in the config. `dorksurvey.take` (everyone), `dorksurvey.admin` (op).

### DorkMotd

`/motd` lets a player set line 2 of the server-list MOTD, with random cooldowns.
Line 1 is your server name. Install DorkMotd on the server and
DorkMotd-Velocity on the proxy. `dorkmotd.set` (everyone), `dorkmotd.bypass` and
`dorkmotd.admin` (op). On the proxy, `/motd reset` and `/motd reload` run from the
console.

## Install

1. Put DorkCore in `plugins/`, plus each other plugin you want.
2. For DorkMotd, also put DorkMotd-Velocity in the proxy's `plugins/`.
3. Start the server once to create the config files, then edit them.
4. Restart. (Most plugins also have a reload command.)

## Configure

Every plugin has its own `config.yml`. All player text is in DorkCore's
`messages.yml`, and all sounds are in DorkCore's `sounds.yml`, so the whole suite
reads and sounds the same.

Cooldowns are in DorkCore's `config.yml`, under `cooldowns:`. Each one is a
fixed number of seconds, or a random range:

```yaml
cooldowns:
  global: { min: 1, max: 2 }   # any command with no entry of its own
  tpa: { min: 4, max: 7 }
  home: 3                      # fixed 3 seconds
```

Each command's use cooldown has the key `cmd-<plugin>-<command>`, for example
`cmd-dorkcore-sethome` or `cmd-rtpqueue-rtp`.

Messages. Each entry in `messages.yml` has `chat`, `actionbar`, `title` and
`subtitle` parts that you turn on or off, plus a `legacy` line for players who
chose Legacy style. Text uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
tags like `<green>`.

Before you go live, check these:
- DorkMenu: set the Discord, Map and Store links, then turn on those buttons.
  They start off.
- DorkMotd-Velocity: set your server name in `format.header`.
- DorkRestart: set `time` and `timezone`.
- SpawnPlatform: check the radius, then turn on `clear.enabled`.
- DorkSurvey: write your own questions and reward.
- DorkCore: `/back` is off. Turn on `back.record-on-death` or
  `back.record-on-teleport` if you want it.

## Build from source

Needs JDK 21 and Maven 3.9.

```
mvn package
```

The jars are in each plugin's `target/` folder. Every push also builds the jars on
GitHub Actions.

## License

Everything is MIT ([LICENSE](LICENSE)), except DorkCombat. DorkCombat started
as a fork of [CelestCombat](https://hangar.papermc.io/Nighter/CelestCombat) by
Nighter, so it keeps that project's license, CC BY-NC-SA 4.0: credit the
authors, no commercial use, and share changes under the same license. See
[backend/DorkCombat/LICENSE](backend/DorkCombat/LICENSE).
