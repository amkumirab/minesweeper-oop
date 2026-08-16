package minesweeper.model;

import minesweeper.exceptions.InvalidCoordinateException;
import minesweeper.model.cell.*;

import java.util.*;

/**
 * The game board — a 2-D grid of cells.
 *
 * Parametric polymorphism: {@code Board<T extends Cell>} lets the engine work
 *   with any concrete cell type, or with the base {@code Cell} class itself.
 * Composition: the Board owns the grid of Cell objects; it does not extend Cell.
 * Encapsulation: the raw grid is private; access is only through coordinated methods.
 * Overloading: two {@code reveal()} variants — by coordinates and by cell object.
 */
public class Board<T extends Cell> {

    // ── encapsulated state ───────────────────────────────────────────────────
    private final int    rows;
    private final int    cols;
    private final T[][]  grid;
    private       int    totalSafeCells;
    private       int    revealedSafeCells;

    // ── constructor ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Board(int rows, int cols) {
        this.rows              = rows;
        this.cols              = cols;
        this.grid              = (T[][]) new Cell[rows][cols];
        this.totalSafeCells    = 0;
        this.revealedSafeCells = 0;
    }

    // ── cell access ──────────────────────────────────────────────────────────

    public T getCell(int row, int col) throws InvalidCoordinateException {
        validateCoords(row, col);
        return grid[row][col];
    }

    public void setCell(int row, int col, T cell) throws InvalidCoordinateException {
        validateCoords(row, col);
        grid[row][col] = cell;
    }

    // ── reveal overloads (overloading polymorphism) ──────────────────────────

    /**
     * Reveals the cell at {@code (row, col)} and returns the result type.
     * Handles flood-fill for empty cells and delegates post-reveal effects to
     * the GameEngine via the returned {@link CellRevealOutcome}.
     *
     * Overloading: this is one of two {@code reveal} signatures.
     */
    public CellRevealOutcome reveal(int row, int col, Player player)
            throws InvalidCoordinateException {

        validateCoords(row, col);
        T cell = grid[row][col];

        if (cell.isRevealed())  return CellRevealOutcome.ALREADY_REVEALED;
        if (cell.isFlagged())   return CellRevealOutcome.FLAGGED;

        cell.reveal();    // marks revealed; throws CellAlreadyRevealedException if double-called

        if (cell instanceof MineCell mine) {
            mine.explode(player);
            return CellRevealOutcome.MINE;

        } else if (cell instanceof TrapCell trap) {
            trap.explode(player);
            return CellRevealOutcome.TRAP;

        } else if (cell instanceof BonusCell bonus) {
            bonus.applyReward(player, this);
            revealedSafeCells++;
            return CellRevealOutcome.BONUS;

        } else if (cell instanceof NormalCell normal) {
            revealedSafeCells++;
            player.addScore(1);
            if (normal.getAdjacentDanger() == 0) {
                floodReveal(row, col, player);
            }
            return CellRevealOutcome.NORMAL;
        }

        return CellRevealOutcome.UNKNOWN;
    }

    /**
     * Overloaded: reveal by cell object reference instead of coordinates.
     * Demonstrates <b>method overloading</b>.
     */
    public CellRevealOutcome reveal(T cell, Player player)
            throws InvalidCoordinateException {
        return reveal(cell.getRow(), cell.getCol(), player);
    }

    // ── flood-fill reveal ────────────────────────────────────────────────────

    private void floodReveal(int startRow, int startCol, Player player) {
        Queue<int[]>  queue   = new LinkedList<>();
        Set<String>   visited = new HashSet<>();

        String startKey = startRow + "," + startCol;
        visited.add(startKey);
        queue.add(new int[]{startRow, startCol});

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int   r   = pos[0];
            int   c   = pos[1];

            for (int[] nb : getNeighbourCoords(r, c)) {
                int    nr  = nb[0];
                int    nc  = nb[1];
                String key = nr + "," + nc;

                if (visited.contains(key)) continue;
                visited.add(key);

                Cell neighbour = grid[nr][nc];
                if (neighbour.isRevealed() || neighbour.isFlagged()) continue;

                // Only auto-reveal normal cells (never mines, traps, or bonuses)
                if (neighbour instanceof NormalCell normal) {
                    neighbour.reveal();
                    revealedSafeCells++;
                    player.addScore(1);
                    if (normal.getAdjacentDanger() == 0) {
                        queue.add(new int[]{nr, nc});
                    }
                }
            }
        }
    }

    // ── bonus helper ─────────────────────────────────────────────────────────

    /**
     * Reveals {@code count} random unrevealed safe (NormalCell) cells.
     * Called by BonusCell when REVEAL_SAFE_CELLS is applied.
     */
    public void revealRandomSafeCells(int count, Player player) {
        List<NormalCell> candidates = new ArrayList<>();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (grid[r][c] instanceof NormalCell nc
                        && !nc.isRevealed()
                        && !nc.isFlagged()) {
                    candidates.add(nc);
                }
            }
        }

        Collections.shuffle(candidates);
        int revealed = 0;
        for (NormalCell nc : candidates) {
            if (revealed >= count) break;
            if (!nc.isRevealed()) {
                nc.reveal();
                revealedSafeCells++;
                player.addScore(1);
                revealed++;
            }
        }
    }

    // ── flag toggle ──────────────────────────────────────────────────────────

    public void toggleFlag(int row, int col) throws InvalidCoordinateException {
        validateCoords(row, col);
        Cell cell = grid[row][col];
        if (!cell.isRevealed()) {
            if (cell.isFlagged()) cell.unflag();
            else                   cell.flag();
        }
    }

    // ── adjacency ────────────────────────────────────────────────────────────

    /**
     * Returns the coordinates of all valid neighbours of (row, col).
     */
    public List<int[]> getNeighbourCoords(int row, int col) {
        List<int[]> result = new ArrayList<>(8);
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int nr = row + dr;
                int nc = col + dc;
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                    result.add(new int[]{nr, nc});
                }
            }
        }
        return result;
    }

    /**
     * Scans every NormalCell and writes its adjacent mine/trap counts.
     * Called once after all cells are placed.
     */
    public void calculateAdjacentCounts() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!(grid[r][c] instanceof NormalCell normal)) continue;
                int mines = 0, traps = 0;
                for (int[] nb : getNeighbourCoords(r, c)) {
                    Cell nbCell = grid[nb[0]][nb[1]];
                    if (nbCell instanceof MineCell) mines++;
                    else if (nbCell instanceof TrapCell) traps++;
                }
                normal.setAdjacentMines(mines);
                normal.setAdjacentTraps(traps);
            }
        }
    }

    // ── win-condition helpers ────────────────────────────────────────────────

    public void setTotalSafeCells(int count) { this.totalSafeCells = count; }

    public boolean isComplete() {
        return totalSafeCells > 0 && revealedSafeCells >= totalSafeCells;
    }

    public int getRevealedSafeCells() { return revealedSafeCells; }
    public int getTotalSafeCells()    { return totalSafeCells;    }

    // ── game-over reveal ─────────────────────────────────────────────────────

    /**
     * Force-reveals all mines and traps (called on game loss).
     */
    public void revealAllDangerCells() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = grid[r][c];
                if ((cell instanceof MineCell || cell instanceof TrapCell)
                        && !cell.isRevealed()) {
                    cell.forceReveal();
                }
            }
        }
    }

    // ── utility ──────────────────────────────────────────────────────────────

    public List<Cell> getAllCells() {
        List<Cell> cells = new ArrayList<>(rows * cols);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                cells.add(grid[r][c]);
            }
        }
        return cells;
    }

    public int getFlaggedCount() {
        int count = 0;
        for (Cell cell : getAllCells()) {
            if (cell.isFlagged()) count++;
        }
        return count;
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }

    // ── validation ───────────────────────────────────────────────────────────

    private void validateCoords(int row, int col) throws InvalidCoordinateException {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new InvalidCoordinateException(row, col, rows, cols);
        }
    }

    // ── inner enum ───────────────────────────────────────────────────────────

    /**
     * The outcome of a single reveal operation, returned to the GameEngine.
     */
    public enum CellRevealOutcome {
        NORMAL, MINE, TRAP, BONUS, ALREADY_REVEALED, FLAGGED, UNKNOWN
    }
}
