# Would You Rather (AI) — Minecraft Fabric mod

An AI-powered Minecraft mod that, every 10 minutes, pops up a freshly generated
"Would You Rather" question. The player picks one of two options and the mod
parses the option's free-text description into real gameplay effects (status
effects, item rewards, hostile mob spawns, etc.).

- **Loader:** Fabric
- **Minecraft:** 1.21.1
- **Java:** 21
- **AI providers:** OpenAI (Chat Completions) or Anthropic (Messages API)

---

## File structure

```
minec-would-you/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── README.md
└── src/main/
    ├── java/com/eazyif/wouldyou/
    │   ├── WouldYouRatherMod.java          # main entrypoint, timer, networking
    │   ├── ai/AIClient.java                # OpenAI + Anthropic HTTP clients
    │   ├── client/
    │   │   ├── WouldYouRatherClient.java   # client entrypoint
    │   │   └── WouldYouRatherScreen.java   # the popup GUI
    │   ├── config/ModConfig.java           # JSON config (config/wouldyou.json)
    │   ├── effects/EffectMapper.java       # keyword -> gameplay effect
    │   ├── network/
    │   │   ├── QuestionPayload.java        # S2C packet
    │   │   └── ChoicePayload.java          # C2S packet
    │   └── question/Question.java          # data record
    └── resources/
        ├── fabric.mod.json
        └── assets/wouldyou/lang/en_us.json
```

## How it works

1. On each player tick the server checks a per-player timer. When the
   configured interval (default **600s**) elapses, the server fires an async
   request to the configured AI provider asking for a JSON-formatted Would You
   Rather question.
2. The response is parsed into a `Question` record and sent to that player's
   client via a `QuestionPayload` S2C packet.
3. The client opens `WouldYouRatherScreen`, a non-dismissible modal with two
   buttons. The player must click one.
4. The client sends a `ChoicePayload` (0 or 1) back to the server.
5. The server feeds the chosen option's text into `EffectMapper`, which scans
   for keywords (`speed`, `jump boost`, `5 diamonds`, `spawn creepers`, …) and
   applies the corresponding gameplay actions.

The full conversation prompt (in `AIClient.SYSTEM_PROMPT`) tells the model to
use **only** keywords the mapper understands, so out-of-the-box matches are
high. Unrecognized text is safely ignored.

## Setup

### 1. Build the mod

Requires JDK 21 and an internet connection for the first Gradle run.

**Linux / macOS:**
```bash
./gradlew build
```

**Windows (cmd or PowerShell):**
```bat
gradlew.bat build
```

The Gradle wrapper is checked into the repo, so no separate Gradle install is
needed. The resulting jar is in `build/libs/wouldyou-1.0.0.jar`.

### 2. Install

Drop the built jar into your Fabric `mods/` directory along with
[Fabric API](https://modrinth.com/mod/fabric-api).

### 3. Configure

Launch Minecraft once with the mod installed. A config file is auto-created at:

```
<minecraft folder>/config/wouldyou.json
```

Edit it:

```json
{
  "provider": "openai",
  "apiKey": "sk-...",
  "model": "gpt-4o-mini",
  "endpoint": "",
  "intervalSeconds": 600,
  "announceEffects": true
}
```

| Field | Description |
| --- | --- |
| `provider` | `openai` or `anthropic` |
| `apiKey` | Your provider API key. Stored only in this file (gitignored). |
| `model` | e.g. `gpt-4o-mini` or `claude-haiku-4-5-20251001` |
| `endpoint` | Optional override (proxies, Azure OpenAI, etc). Empty = default. |
| `intervalSeconds` | Seconds between popups. Min `1` (clamped to ≥ 1 tick second). |
| `announceEffects` | If true, the chat log shows what effects were applied. |

If `apiKey` is empty (or the API call fails), the mod uses a safe fallback
question so gameplay never breaks.

### 4. Multiplayer

Install the mod on the **server** and on every connecting client. The server
holds the API key — clients never see it.

## Effect keywords supported

Status effects: `speed`, `jump boost`, `night vision`, `regeneration`,
`strength`, `resistance`, `fire resistance`, `water breathing`, `haste`,
`luck`, `glowing`, `slowness`, `weakness`, `hunger`, `blindness`, `poison`,
`wither`, `mining fatigue`, `nausea`, `levitation`.

Items (with optional `<N> ` quantity prefix, e.g. `5 diamonds`): `diamond`,
`emerald`, `gold`, `iron`, `arrow`, `food` / `cooked beef` / `steak`.

Spawns: `creeper`, `zombie`, `skeleton` (count parsed similarly).

Misc: `lightning`, `set on fire`, `extinguish`, `less/more health`, `full heal`.

Add new keywords by extending `EffectMapper#apply`.

## Development tips

- Run the dev client: `./gradlew runClient`
- Run the dev server: `./gradlew runServer`
- Logs are tagged `[wouldyou]`.

## License

MIT.
