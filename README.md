# FM AI Assistent for Linux and Windows

FM AI Assistent is an AI assistant companion for Football Manager 26 running on Linux or Windows 11.

It reads FM26 data from RAM and makes the data available to the in-app chat and to AI clients through MCP. Use it to help with buying and selling players, finding profitable young talents, comparing squads, checking club finances, and giving tactical advice based on the players in your save.

The app also includes a frontend where you can search and filter the data yourself. Pages:

- Desk (`/`) — players, clubs, competitions
- Shortlist (`/shortlist`) — tactical buys (`fm26_transfer_shortlist`; wonderkid age cap optional)
- Moneyball (`/moneyball`) — value signings
- Squad trim (`/squad-trim`) — sell / loan / keep
- First XI (`/first-xi`) — XI from the live RAM formation or a pasted tactic
- Contracts (`/contracts`) — contract expiry, wage and squad-health review
- Academy (`/academy`) — in-house youth and development candidates
- Compare (`/compare-squads`) — two clubs, best player per position
- Chat (`/chat`) — in-app OpenRouter chat using the same `fm26_*` tools

You can inspect attributes, positions, reputations, contracts, salaries, asking prices, and budgets. Preferred-move traits are filled when RAM name vectors match. Morale, form, and match stats stay empty until those offsets are validated. In/out-of-possession roles are not read from RAM; paste them on First XI, or load an `.fmf` on Chat, if you want role-fit scoring. Status fields that fail to read are shown as unknown rather than false.

Display currency is chosen in Settings. RAM snapshots persist in a local H2 file (`fm-ai-assistent-db`) under `%USERPROFILE%\\.fm-ai-assistent` on Windows (or `~/.fm-ai-assistent` on Linux). Existing files beside an older jar or executable are migrated there with a retained backup on first launch.

## How To Install

### Native Image

The project includes a Spring AOT/native-image profile. Build it with GraalVM for JDK 25 and the Native Image component installed. The native image is an optional deployment form; the Java jar and Electron build remain the supported Windows desktop paths.

```bash
mvn.cmd -Pnative -DskipTests native:compile
```

After compiling a Windows image, run the native smoke test with `npm run smoke-native`. Set `FM_AI_NATIVE_PATH` when the executable is stored outside `target\fm-ai-assistent.exe`.

The binary is written to `target/fm-ai-assistent`. Make it executable on Linux:

```bash
chmod +x ./target/fm-ai-assistent
```

Start it from a terminal:

```bash
./target/fm-ai-assistent
```

On Windows, the command writes `target\fm-ai-assistent.exe`. Native-image compilation is platform-specific, so a Windows image must be built on Windows and is not committed to the repository.

### Option 1: Java Jar

Minimum requirement: Java 25.

Download the jar and run:

```bash
java -jar ./fm-ai-assistent.jar
```

On Windows, run the command from PowerShell or Command Prompt:

```powershell
java -jar .\fm-ai-assistent.jar
```

Start FM26 and load a save before loading RAM data. Run FM AI Assistent as the same Windows user as FM26. Administrator privileges are normally not required, but may be needed if FM26 itself was started as administrator.

The application starts on:

```text
http://127.0.0.1:8080
```

After a load, `fm26_current_tactic` reports the live formation and selected XI when the scan hits. In-app chat uses an OpenRouter key from Settings. You can also connect an MCP client such as Claude Desktop to `/mcp`.

### Option 2: Run from source (development)

Requirements: JDK 25 and Maven 3.9+ (on Windows use `mvn.cmd`, not the `mvn` shell script).

Build and start:

```bash
mvn.cmd -DskipTests package
java -jar target/fm-ai-assistent-0.4.0-SNAPSHOT.jar
```

Or start directly with Maven:

```bash
mvn.cmd spring-boot:run
```

On Windows, double-click `start.bat` in this folder. It opens a terminal, sets `JAVA_HOME`, and runs `mvn.cmd spring-boot:run`.

The application starts on http://127.0.0.1:8080. Keep FM26 running with a save loaded, then click "Load from RAM" in the UI before using chat or MCP tools.

## Use AI Assistent

Keep FM26 running with your save loaded. Start FM AI Assistent and click Load from RAM (or call `fm26_load_from_ram`) once; a persisted snapshot is enough until the save changes.

### In-app chat

Open **Chat** (`/chat`). Set an OpenRouter API key in Settings. Chat calls the same `fm26_*` tools as `/mcp`.

On Chat you can also:

- enter the location of a `.fmf` tactic file;
- upload a `.fmf` tactic file; or
- optionally select a folder containing an FMF plus extra screenshots/readable exports.

The application reads the FM26 archive catalog, decrypts and decompresses the embedded tactic resource, and converts the tactic name, tactical style, mentality, in-possession roles/duties, and out-of-possession roles/duties into AI-readable text. The `.fmf` file is sufficient: screenshots and Football Manager Resource Archiver are not required. The file is processed locally and is not uploaded to a separate conversion service.

### MCP tools

Recruitment: `fm26_transfer_shortlist`, `fm26_moneyball_shortlist`, `fm26_wonderkid_shortlist`.
Squad: `fm26_sell_shortlist`, `fm26_compare_squads`, `fm26_compare_players`, `fm26_best_xi`, `fm26_current_tactic`.
Lookup: `fm26_status`, `fm26_load_from_ram`, `fm26_find_clubs`, `fm26_find_players`, `fm26_find_competitions`, `fm26_get_club_context`, `fm26_get_player_details`, `fm26_get_role_attributes`.
Research: `fm26_ram_table_counts` (live offset-table slot counts; not a manager workflow).

`fm26_load_from_ram` starts an asynchronous load and returns a `job_id`. Call `fm26_status` to monitor the phase, progress and final counts. The web UI still displays live progress while loading.

Money values are raw pounds. `asking_price=null` means unknown, not free.

For a first XI, call `fm26_current_tactic` then `fm26_best_xi` with `managingClub`. Omit `tacticSlots` to use the RAM formation; pass `position,inPossessionRole,outOfPossessionRole` lines (not `DMC:`) when you want role fit.

### Claude or other MCP clients

The HTTP MCP server is `http://127.0.0.1:8080/mcp`. Use the server name `fm-ai-assistent`.

Claude Desktop example:

```json
{
  "mcpServers": {
    "fm-ai-assistent": {
      "url": "http://127.0.0.1:8080/mcp"
    }
  }
}
```

Restart Claude Desktop after changing the config. The web server binds to `127.0.0.1` by default; keep it local.

## Use of the app
- Start Football Manager 26 and load your game.
- Start fm-ai-assistent
- Open http://127.0.0.1:8080
- Load from RAM
- Use Shortlist / Moneyball / Squad trim / First XI / Compare, or Chat (OpenRouter), or connect an MCP client to `/mcp`

## Known limits

- Morale, form, appearances, goals and assists are not read from RAM yet.
- In/out-of-possession roles are not in RAM. Use Chat `.fmf` import or paste roles on First XI.
- Preferred-move traits are filled only when RAM name vectors match.
- Staff and retired people without playable positions are excluded from transfer and moneyball pools.
- In-app chat sends the selected save context and conversation to OpenRouter. Use MCP-only mode if you do not want to configure an OpenRouter key.

## AI Examples

### prompt
```
i am playing as Feyenoord, i am looking for a new DL that can play in possession as a inverted full back and out of possession as a holding full back. I want the new player to become a immediatly starter or a young player
  that becomes better then my current DL. use the fmaiassistent mcp.
```

```
Best pick depends on money.

  1. Ramon Hendriks, Stuttgart
  Best answer if you can stretch above budget.
  CA 135 / PA 145, age 23, £11.85m, £13.3k/w. D(LC), DL 19, DC 20, DM 13.
  Starter now over Smal/Bos, and higher ceiling than Bos. Holding FB fit good: marking 14, tackling 14, positioning 13, strength 16, pace 16, work rate 17. Inverted possession okay, not elite: passing 11, first touch 11,
  technique 11.

  2. Jayden Oosterwolde, Fenerbahce
  Best immediate starter inside current budget.
  CA 135 / PA 140, age 24, £8.2m, £35.4k/w. D(LC), DL 16, DC 20.
  Strong holding FB: tackling 15, marking 14, strength 15, pace 16, teamwork 16, bravery 18. But inverted possession weaker: first touch 8, technique 11, vision 9. Also temperament 3 = discipline/personality risk.

  3. Frans Kratzig, Salzburg
  Best tactical fit for inverted full back, but not immediate upgrade.
  CA 126 / PA 140, age 22, £4.75m, £16.6k/w. D/WB(L), DM, M(C), DL 20, WBL 18, DM 15, MC 15.
  Possession fit strong: technique 15, first touch 14, passing 13, teamwork 16, versatility 19. Weak as holding FB: marking 9, positioning 10, strength 8. Ceiling equals Bos, not clearly better.

  4. Jesus Angulo, Tigres
  Solid immediate defensive option.
  CA 133 / PA 138, age 27, £6.85m, £14.4k/w. D(LC), WB(L), DL 20, DC 20.
  Good holding FB: positioning 15, tackling 14, marking 13, concentration 14, composure 14. Possession okay only: passing 12, technique 12. No DM/MC fit.

  Avoid for this role
  Jan-Niklas Beste: CA 135 and affordable at £8.55m, but more winger/playmaker than holding full back. Marking 9, positioning 9, strength 9, no DM.
  John Tolkin: cheap, nice squad option, but CA 123. Not starter over Smal/Bos.

  My call: Ramon Hendriks if you can raise ~£3m extra. If budget fixed, buy Oosterwolde for immediate XI or Kratzig if you value role fit and development over current CA.
```

### prompt

```
this is my tactic. What would your first 11 be using this tactic?  which positions do you think we should definitly get an upgrade? And what roles or positions should i change in my tactic to better suite my players?
  Player,in possesion role,out of possesion role
  GK,Ball playing GK,Sweeper keeper
  DL,Inverted Full Back, Holding full back
  DC,Centreback,CentreBack
  DC,Ball playing Centreback, Centreback
  DR,Inverted Wing Back, Pressing Full Back
  DMC: Deep lying playmaker, Defensive Midfielder
  MC:Midfield Playmaker,Central Midfielder
  MC:Advanded Playmaker,Pressing Central Midfielder
  AML:Wide Forward,Winger
  AMR:Winger,Winger
  ST:Deep Lying Forward,Tracking Centre Forward
```

### response
```
First XI

  GK: Wellenreuther
  Good SK/BPGK fit. Kicking 15, throwing 15, rushing out 14, one-on-ones 15.

  DL: Smal for now
  Best current DL. Passing 14, concentration 15, work rate 15. Bos has more PA but lower now.

  DC: Watanabe
  Best pure CB. CA 136, marking/tackling/positioning 14, teamwork 15.

  DC: Ahmedhodzic as BPD
  Passing 13, vision 13, composure 13. Not elite BPD, but best fit next to Watanabe.

  DR: Read
  Must start. CA 136/PA 152. Great IWB/pressing FB hybrid: passing 14, first touch 14, technique 14, pace 15, DR 20, DM 13.

  DMC: Moder
  Better than Hwang for DLP/DM balance. DM 19, MC 20, passing 14, tackling 13, strength 13.

  MC: Hwang
  Best player. Use as main playmaker, not DMC stopper. Passing 16, technique 16, decisions 16, teamwork 18.

  MC: Sem Steijn
  Best advanced CM. CA 139. Off ball 17, decisions 15, first touch/technique/vision 15. Presses well: work rate 17.

  AML: Sterling
  Best wide forward. Dribbling 15, off ball 17, acceleration 15, AM(L) 20.

  AMR: Hadj Moussa
  Best natural right winger. Left-footed, AMR 20, dribbling 17, crossing 14. Risk: teamwork 9, determination 5, consistency 9.

  ST: Ueda
  Best striker. Finishing 15, composure 15, anticipation 15, work rate 17.

  Definite Upgrades

  1. DL
     Biggest hole. Smal 131, Bos 129. For your IFB/HFB role, buy Ramon Hendriks if possible. If budget fixed, Oosterwolde or Kratzig depending on need.

  2. AMR
     Hadj Moussa has talent but bad mental profile for possession/pressing. Need more reliable winger: teamwork, decisions, work rate, consistency.

  3. BPD
     Ahmedhodzic okay, not top. Need CB with passing 14+, composure 14+, decisions 14+, plus normal CB defending.

  Tactic Changes

  Change ST in possession: Deep Lying Forward -> Advanced Forward or Pressing Forward.
  Ueda is scorer/runner, not creator. Passing 10, first touch 12. His best stuff is finishing, off ball, work rate, aggression.

  Change DMC: Deep Lying Playmaker -> Defensive Midfielder / Half Back if Hwang plays MC.
  Moder can pass enough, but team already has Hwang + Steijn. You need rest defence because both fullbacks invert/step in.

  Change AMR out of possession: Winger -> Pressing Winger only if Hadj Moussa accepts it.
  He has pace/dribble, but weak teamwork/determination. If he underperforms, use Valente AMR for more team structure.

  Change left side: keep Sterling Wide Forward, but DL should be more conservative.
  Smal/Hendriks as holding/inverting support behind Sterling. Do not make DL too aggressive.

  Best shape with current squad: asymmetric possession. Read inverts from right, Smal holds left, Hwang controls MC, Steijn attacks, Ueda runs channels.
```
