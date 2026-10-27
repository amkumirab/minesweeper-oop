# 💣 Minesweeper — Extended Edition

A feature-rich Minesweeper game with Swing and console interfaces, built with Java 17 and no external dependencies. It includes safe first-click generation, configurable difficulty, traps, bonuses, lives, undo support, and an optional time limit.

---

## Quick Start

### Windows

```bat
compile.bat
run.bat

REM Optional — run the console interface
run.bat --console
```

### Linux / macOS

```bash
# 1 — compile
bash compile.sh

# 2 — run the Swing interface
bash run.sh

# Optional — run the console interface
bash run.sh --console
```

Or manually:
```bash
javac -encoding UTF-8 -d out -sourcepath src $(find src -name "*.java")
java -cp out minesweeper.Main
```

---

## Tests

Run the dependency-free regression suite with JDK 17 or newer:

```bat
test.bat
```

On Linux / macOS:

```bash
bash test.sh
```

The tests cover cell effects, repeated and flagged reveals, flood-fill safety,
first-click generation, Freeze handling, a custom reward cell, and player move
history (defensive copies, newest-first order, and the twenty-move limit).
They also cover time-limit polling, action rejection after timeout, and console
timeout handling. Custom dangerous cells are tested for neighbour counts,
flood-fill boundaries, and loss-time reveals without triggering their effects.
Force-reveal tests verify flag clearing and correct mine rendering in Swing.
Encapsulation tests check the public engine boundary, immutable cell snapshots,
read-only player/time queries, and rendering through cell views.
Undo tests cover blocked attempts, retrying after unflagging, missing tokens,
winning reveals, and console messages. Random safe-reveal tests verify actual
counts and protect flagged and special cells.
Console input tests cover end-of-input during setup, gameplay, and replay,
including after invalid input. The session exits with an `Input closed` message
instead of a stack trace; the scanner remains responsible only for reading input.
A failing test makes the command exit with a nonzero status.
Compiled files stay in `out/`.

### Desktop GUI Tests

Run `gui-test.bat` on Windows or `bash gui-test.sh` on Linux / macOS with a
desktop display. These optional integration tests open real Swing windows and
close them automatically. They are separate from the regular headless suite.
They verify wins through normal clicks, flood fill, bonuses, and Undo; final
score text; OK and window-close dismissal; one dialog per game (including nested
events); reset for a new game; and no victory dialog on loss.

---

## Project Structure

```
src/minesweeper/
├── Main.java                        ← entry point
├── interfaces/
│   ├── Revealable.java              ← abstraction: reveal behaviour
│   ├── Flaggable.java               ← abstraction: flag behaviour
│   ├── Explodable.java              ← abstraction: dangerous cells
│   └── Rewardable.java              ← abstraction: rewarding cells
├── model/
│   ├── BonusType.java               ← enum: EXTRA_LIFE | REVEAL_SAFE_CELLS | UNDO_LAST_MOVE
│   ├── Player.java                  ← lives, score, debuffs, move history
│   ├── Board.java                   ← generic Board<T extends Cell>
│   └── cell/
│       ├── Cell.java                ← abstract base class
│       ├── NormalCell.java          ← shows adjacent danger count
│       ├── MineCell.java            ← implements Explodable
│       ├── TrapCell.java            ← implements Explodable (3 trap effects)
│       └── BonusCell.java           ← implements Rewardable (3 bonus types)
├── engine/
│   ├── CellView.java                ← immutable cell display snapshot
│   ├── GameSettings.java            ← immutable config + Builder pattern
│   ├── GameState.java               ← enum: NOT_STARTED → IN_PROGRESS → WON/LOST
│   ├── GameTimer.java               ← elapsed time + countdown
│   ├── RevealResult.java            ← value object returned by GameEngine
│   └── GameEngine.java              ← core orchestrator (composition)
├── exceptions/
│   ├── CellAlreadyRevealedException ← unchecked
│   ├── InvalidCoordinateException   ← checked
│   ├── GameOverException            ← checked (won/lost flag)
│   └── InvalidGameSettingsException ← checked
└── ui/
    ├── CellButton.java              ← graphical cell rendering
    ├── SetupDialog.java             ← graphical game configuration
    ├── SwingUI.java                 ← Swing event handling and layout
    └── ConsoleUI.java               ← console input, rendering, game loop
```

---

## Architecture

### Core Design

| Concept | Where | Purpose |
|---|---|---|
| **Encapsulation** | `GameEngine`, `CellView`, `Cell`, `Player`, `Board` | Public engine queries expose immutable values, not live mutable objects. UIs perform gameplay changes through engine actions. |
| **Abstraction** | `Cell` (abstract class) + all four interfaces | Cells share a common reveal contract; interfaces describe capabilities such as exploding, rewarding, and flagging. UIs read `CellView` instead of the implementation. |
| **Inheritance** | `NormalCell`, `MineCell`, `TrapCell`, `BonusCell` all extend `Cell` | Common reveal guards, flag logic, and string conversion live once in the base class. |
| **Polymorphism — Overriding** | `onReveal(Player,Board)`, `getRevealDescription()`, `getRevealedSymbol()`, `getType()` | Each subclass applies its own gameplay effect and supplies its description and display symbol. |
| **Polymorphism — Overloading** | `Board.reveal(int,int,Player)` / `Board.reveal(T,Player)` and `Cell.reveal()` / `Cell.reveal(Player,Board)` | Reveal by coordinates or by cell reference; distinguish effect-free reveals from gameplay reveals. |
| **Polymorphism — Parametric** | `Board<T extends Cell>` | The board is generic — it can hold `Cell` or any subtype, keeping it reusable. |
| **String conversion** | `Cell.toString()` | Provides a text representation of a cell; the UIs use their own styled rendering. |

### Extensibility and Composition

| Concept | Where | Purpose |
|---|---|---|
| **Composition** | `GameEngine` owns `Board`, `Player`, `GameSettings`, `GameTimer` | Engine *uses* these objects; it does not *extend* them. |
| **Subtyping / Multityping** | `MineCell extends Cell` and implements `Explodable`; `BonusCell extends Cell` and implements `Rewardable` | A cell can inherit shared cell behavior while also implementing an additional capability. |
| **Extensibility** | `TrapCell.TrapEffect` enum; `BonusType` enum; `Rewardable` / `Explodable` interfaces | Interfaces provide extension points for new cell capabilities; enum-based effects remain simple and explicit to update. |

### Supporting Techniques

| Requirement | Implementation |
|---|---|
| **Custom exceptions** | `GameOverException`, `InvalidCoordinateException`, `CellAlreadyRevealedException`, `InvalidGameSettingsException` — each has a clear, specific meaning. |
| **Modularity** | `interfaces/`, `model/`, `engine/`, `exceptions/`, `ui/` — each layer has one responsibility and does not reach into another layer's internals. |
| **Reusability** | `Cell` can be extended into any new type. `Board<T>` can be reused for different games. `GameTimer` is completely independent. |
| **Template Method** | `Cell.reveal(Player,Board)` is `final` and calls the abstract `onReveal(Player,Board)` hook — subclasses customise effects while sharing revealed/flagged guards. |
| **Builder pattern** | `GameSettings.Builder` produces valid, immutable settings or throws `InvalidGameSettingsException` before a bad state can exist. |

### Read-only Engine API

Call `initialise()` before querying cells or formatted time. Both UIs read the
same public API:

- `getCell(row, col)` returns a new immutable `CellView`, not a live `Cell`.
  Its private final fields contain only primitive values and strings. A saved
  view stays unchanged; query again after an action to display the current state.
- `getPlayerName()`, `getLives()`, `getScore()`, and `isUndoAvailable()` return
  player information without exposing the mutable `Player`.
- `isTimerRunning()`, `getRemainingSeconds()`, `getFormattedElapsed()`, and
  `getFormattedRemaining()` expose time information without exposing `GameTimer`.
- Gameplay changes go through `revealCell()`, `toggleFlag()`, and `useUndo()`.

`getBoard()`, `getPlayer()`, and `getTimer()` have package-private access.
Callers outside `minesweeper.engine` must use the queries above rather than
mutating engine-owned objects. Engine regression tests live in that package so
they can prepare internal scenarios; the public-boundary tests live outside it.
The model classes remain mutable for use inside the game logic. This boundary
prevents UI code from revealing a cell without updating the board's counters.

---

## Game Features

### Standard Minesweeper
- Configurable board size (5×5 to 30×30)
- Beginner / Intermediate / Expert presets
- Flood-fill auto-reveal for empty cells
- Flag system to mark suspected mines
- Safe first click — mines generated *after* the first reveal

### Extended Features
| Feature | Description |
|---|---|
| 🪤 **Trap cells** | Three effects: _Lose a Life_, _Expose a random mine_, _Freeze your next move_ |
| 🎁 **Bonus cells** | Three rewards: _Extra Life_, _Reveal 3 safe cells_, _Undo token_ |
| ❤ **Lives system** | Multiple lives; game ends only when all are spent |
| ⏱ **Time limit** | Countdown timer; losing when it hits zero |
| ↩ **Undo token** | Granted by a bonus; reveals one unflagged normal cell on demand; kept if none is available |
| 🏆 **Victory dialog** | Shows the final score once per game; dismissing it keeps the completed board visible |

---

## Commands

```
reveal <row> <col>   — reveal a cell       (aliases: r, open, o)
flag   <row> <col>   — toggle a flag       (alias: f)
undo                 — use undo token
help                 — show commands
quit                 — exit
```

Coordinates are **1-indexed** (top-left = row 1, col 1).

---

## Requirements

- Java 17 or higher (uses switch expressions and pattern matching for `instanceof`)
- No external libraries — pure Java SE

---

## Design Decisions

1. **`Cell.reveal(Player,Board)` is `final`** — guarding repeated and flagged gameplay reveals before calling the subclass's `onReveal(Player,Board)` implementation. The no-argument overload only marks a cell revealed and retains the duplicate-reveal exception.
2. **`GameSettings` is immutable** — built via a `Builder` that validates all invariants before the object exists. Impossible to create an invalid settings object.
3. **Cell effects use dynamic dispatch** — `Board` calls `cell.reveal(player, board)` without selecting an effect by concrete class. It tracks safe reveals and performs flood-fill, then the engine uses the typed `Board.CellRevealOutcome` to check game state and build a message. NORMAL and BONUS outcomes count toward victory; MINE and TRAP do not. New subclasses implement `onReveal(Player,Board)` and can override `getRevealDescription()` without changing the board's effect dispatch.
4. **Board generation is deferred** to the first click — guaranteeing a safe 3×3 zone around it, matching real Minesweeper behaviour.
5. **`GameOverException` is a checked exception** — callers (the UI) are *forced* by the compiler to handle game-ending events, preventing them from being silently ignored.
6. **Freeze applies to valid reveals** — out-of-bounds, flagged, and already-revealed targets leave the pending Freeze effect untouched. A valid attempt consumes Freeze and skips its target; the message reports whether an unflagged normal cell was actually revealed instead. If none is available, the target stays hidden and can be tried again.
7. **Move history stays encapsulated** — `Player.peekLastMove()` returns a defensive copy, so changing the returned coordinates cannot change the player's stored move. The history keeps the newest twenty moves; `Deque.removeLast()` removes the oldest directly without rebuilding the collection or using an extra loop.
8. **The engine owns time-limit enforcement** — `GameEngine.checkTimeLimit()` ends an expired active game without performing a move. Reveal, flag, and undo actions check it before changing gameplay state. Swing polls it on timer ticks; the console checks it after reading each input line, including help and blank input. The console waits for input rather than running a background thread. Unstarted and finished games are unchanged by polling.
9. **`Explodable` identifies dangerous cells** — the board includes custom implementations in neighbour danger totals and reveals them on loss without calling `explode()`. Mine and trap counts retain their existing meanings; other dangerous cells contribute separately to the total. UI-specific styling uses the type names carried by `CellView`.
10. **Force reveal clears the cell's flag** — `Cell.forceReveal()` shows the cell without applying gameplay effects. Clearing the flag in the model keeps text and Swing rendering consistent, including dangerous cells exposed on loss.
11. **The engine owns mutable game state** — public queries return scalar values or detached `CellView` objects. Neither UI receives the live board, player, timer, or cells. The view stores the cell's polymorphic symbol; each UI keeps its own styling, with the symbol as the fallback for custom cell types.
12. **Undo consumes a token only after a successful reveal** — `Board.revealRandomSafeCells(count, player)` returns the number actually revealed, up to the requested count. Flagged, revealed, and special cells are excluded; a nonpositive count returns zero. `GameEngine.useUndo()` returns false without consuming the token when no eligible cell is available. On success, it consumes the token before checking for a win. Both UIs distinguish a blocked reveal from a missing token. The board reports the operation's result; the engine owns the token policy. Existing source callers may ignore the new return value, but compiled callers must be rebuilt.
13. **Victory feedback belongs to the UI** — `GameEngine` keeps the existing win condition and game-ending event. Swing refreshes the completed board and stops its timer before showing a modal `JOptionPane` with the final score. A private per-game flag is set before opening the dialog, because modal dialogs process nested events; this prevents duplicate congratulations. Starting a new game resets that flag. Dismissing the dialog does not restart the game.

`toggleFlag(int, int)` can throw `GameOverException` when time expires, in addition
to `InvalidCoordinateException` for invalid coordinates. Both interfaces handle
that game-ending event. In an expired game, timeout takes precedence over
coordinate validation, so invalid input cannot postpone the loss.
