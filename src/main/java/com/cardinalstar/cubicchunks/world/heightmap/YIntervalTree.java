package com.cardinalstar.cubicchunks.world.heightmap;

import java.util.Arrays;

import com.cardinalstar.cubicchunks.network.CCPacketBuffer;
import com.cardinalstar.cubicchunks.util.Coords;
import com.cardinalstar.cubicchunks.util.XSTR;

/**
 * An interval set that tracks which integer Y coordinates are occupied (have a solid block).
 * <p>
 * Internally, contiguous runs of occupied Y values are merged into disjoint intervals [lo, hi].
 * The structure is a treap (BST ordered by {@code lo}, heap-ordered by random {@code prio}),
 * backed entirely by parallel {@code int[]} arrays so that no per-node Java objects are allocated
 * after construction.
 * <p>
 * All operations are O(log n) where n is the number of disjoint intervals.
 */
public final class YIntervalTree {

    private static final int NULL = -1;
    private static final int INITIAL_CAPACITY = 8;

    // Node pool: 5 parallel int[] arrays indexed by slot number.
    // When a node is freed, left[node] is repurposed as the free-list next pointer.
    private int[] lo; // interval lower bound (inclusive)
    private int[] hi; // interval upper bound (inclusive)
    private int[] left; // left child index, or free-list next when node is freed
    private int[] right; // right child index
    private int[] prio; // treap priority (random, maintains heap invariant)

    private int root = NULL;
    private int freeHead = NULL; // head of free-node singly-linked list (through left[])
    private int nodeCount = 0; // live node count
    private int capacity;

    /** Cached hi value of the rightmost node; {@link Coords#NO_HEIGHT} when tree is empty. */
    private int topY = Coords.NO_HEIGHT;
    /** Cached lo value of the leftmost node; {@link Coords#NO_HEIGHT} when tree is empty. */
    private int bottomY = Coords.NO_HEIGHT;

    // Temporary outputs of split() — avoids per-call array allocation.
    // Only valid immediately after a split() call; consumed before any subsequent split().
    private int splitL;
    private int splitR;

    private final XSTR rng = new XSTR();

    public YIntervalTree() {
        capacity = INITIAL_CAPACITY;
        lo = new int[capacity];
        hi = new int[capacity];
        left = new int[capacity];
        right = new int[capacity];
        prio = new int[capacity];
        Arrays.fill(left, NULL);
        Arrays.fill(right, NULL);
    }

    // ---- Public API -------------------------------------------------------

    /**
     * Marks Y as occupied. No-op if already occupied.
     * Adjacent or bridging intervals are merged automatically.
     */
    public void add(int y) {
        // Single BST pass finds both predecessor (max lo <= y) and successor (min lo > y).
        int node = root;
        int pred = NULL, succ = NULL;
        while (node != NULL) {
            if (lo[node] <= y) {
                pred = node;
                node = right[node];
            } else {
                succ = node;
                node = left[node];
            }
        }

        if (pred != NULL && hi[pred] >= y) {
            return; // y is already inside [lo[pred], hi[pred]]
        }

        boolean leftAdj = pred != NULL && hi[pred] == y - 1;
        boolean rightAdj = succ != NULL && lo[succ] == y + 1;

        if (leftAdj) {
            if (rightAdj) {
                // Bridge: absorb succ into pred in-place, then delete succ node.
                // hi[pred] = hi[succ] — no BST re-keying needed (lo[pred] unchanged).
                // lo[succ] = y+1 is provably not any other interval's lo, so BST still valid after succ removal.
                hi[pred] = hi[succ];
                deleteInterval(lo[succ]);
                // topY: hi[succ] <= topY always (was never > max), so unchanged.
                // bottomY: lo[pred] unchanged, so unchanged.
            } else {
                // Extend pred's right end in-place — hi is not the BST key, zero structural changes.
                hi[pred] = y;
                if (y > topY) topY = y;
                // bottomY unchanged: lo[pred] stays the same.
            }
        } else if (rightAdj) {
            // Extend succ's left end in-place.
            // Safe: y is unoccupied, so no other interval has lo==y; BST property holds.
            lo[succ] = y;
            if (y < bottomY || bottomY == Coords.NO_HEIGHT) bottomY = y;
            // topY unchanged: hi[succ] stays the same.
        } else {
            // Isolated point: insert a brand-new singleton interval.
            insertInterval(y, y);
            if (y > topY) topY = y;
            if (bottomY == Coords.NO_HEIGHT || y < bottomY) bottomY = y;
        }
    }

    /**
     * Marks Y as unoccupied. No-op if not occupied.
     * Splits the containing interval if Y is in its interior.
     */
    public void remove(int y) {
        int pred = findPred(y);

        if (pred == NULL || hi[pred] < y) {
            return; // y is not in any interval
        }

        int predLo = lo[pred];
        int predHi = hi[pred];

        if (predLo == y && predHi == y) {
            // Singleton: delete the node, then recompute both extremes via tree walk
            deleteInterval(predLo);
            if (y == topY) updateTopY();
            if (y == bottomY) updateBottomY();
        } else if (predHi == y) {
            // Trim right end in-place (hi is not the BST key)
            hi[pred] = y - 1;
            // No other interval has hi >= y (y was global max), so new topY = y-1
            if (y == topY) topY = y - 1;
            // bottomY unchanged: lo[pred] is still predLo, still the minimum if it was before
        } else if (predLo == y) {
            // Trim left end in-place (safe: no interval has lo in (y, predHi])
            lo[pred] = y + 1;
            // lo[pred] was the global min, now it's y+1
            if (y == bottomY) bottomY = y + 1;
            // topY unchanged: predHi > y so the interval still contributes hi = predHi
        } else {
            // Interior split: shrink left half in-place, insert right half.
            // y cannot equal topY (predHi > y would exceed topY) or bottomY (predLo < y).
            hi[pred] = y - 1;
            // Fast path (expected ~50%): if pred has higher priority than the new node,
            // [y+1, predHi] belongs in pred's right subtree. All nodes there have
            // lo > predHi > y, so the walk always goes left and split gives splitL=NULL.
            int n = allocNode(y + 1, predHi);
            if (prio[pred] >= prio[n]) {
                int parent = NULL, cur = right[pred];
                while (cur != NULL && prio[cur] >= prio[n]) {
                    parent = cur;
                    cur = left[cur];
                }
                right[n] = cur;
                left[n] = NULL;
                if (parent == NULL) right[pred] = n;
                else left[parent] = n;
            } else {
                insertAllocated(n, y + 1);
            }
        }
    }

    /**
     * Returns the maximum occupied Y coordinate, or {@link Coords#NO_HEIGHT} if the tree is empty.
     * O(1).
     */
    public int getTopY() {
        return topY;
    }

    /**
     * Returns the minimum occupied Y coordinate, or {@link Coords#NO_HEIGHT} if the tree is empty.
     * O(1).
     */
    public int getBottomY() {
        return bottomY;
    }

    /**
     * Returns the first Y' &gt;= {@code startY} that is NOT occupied (first air at or above startY).
     * If {@code startY} is already unoccupied, returns {@code startY}.
     * O(log n).
     */
    public int getTopAirY(int startY) {
        int pred = findPred(startY);
        if (pred == NULL || hi[pred] < startY) {
            return startY; // startY is already air
        }
        // startY is inside [lo[pred], hi[pred]]; intervals are non-adjacent so hi[pred]+1 is air
        return hi[pred] + 1;
    }

    /**
     * Returns the first Y' &lt;= {@code startY} that is NOT occupied (first air at or below startY).
     * If {@code startY} is already unoccupied, returns {@code startY}.
     * O(log n).
     */
    public int getBottomAirY(int startY) {
        int pred = findPred(startY);
        if (pred == NULL || hi[pred] < startY) {
            return startY; // startY is already air
        }
        // startY is inside [lo[pred], hi[pred]]; intervals are non-adjacent so lo[pred]-1 is air
        return lo[pred] - 1;
    }

    /**
     * Returns true if Y is occupied.
     */
    public boolean get(int y) {
        int pred = findPred(y);
        return pred != NULL && hi[pred] >= y;
    }

    /**
     * Sets Y as occupied ({@code present=true}) or unoccupied ({@code present=false}).
     * Returns true when the tree was updated.
     * Uses a single BST traversal to find predecessor and successor, avoiding the double
     * traversal that would occur if get() + add()/remove() were called separately.
     */
    public boolean set(int y, boolean present) {
        // Single combined pred+succ traversal
        int node = root;
        int pred = NULL, succ = NULL;
        while (node != NULL) {
            if (lo[node] <= y) {
                pred = node;
                node = right[node];
            } else {
                succ = node;
                node = left[node];
            }
        }

        boolean was = pred != NULL && hi[pred] >= y;
        if (present == was) return false;

        if (present) {
            // Add path — same in-place adjacency logic as add()
            boolean leftAdj = pred != NULL && hi[pred] == y - 1;
            boolean rightAdj = succ != NULL && lo[succ] == y + 1;
            if (leftAdj) {
                if (rightAdj) {
                    hi[pred] = hi[succ];
                    deleteInterval(lo[succ]);
                } else {
                    hi[pred] = y;
                    if (y > topY) topY = y;
                }
            } else if (rightAdj) {
                lo[succ] = y;
                if (y < bottomY || bottomY == Coords.NO_HEIGHT) bottomY = y;
            } else {
                insertInterval(y, y);
                if (y > topY) topY = y;
                if (bottomY == Coords.NO_HEIGHT || y < bottomY) bottomY = y;
            }
        } else {
            // Remove path: pred already found (it contains y since was==true), no second traversal
            int predLo = lo[pred];
            int predHi = hi[pred];
            if (predLo == y && predHi == y) {
                deleteInterval(predLo);
                if (y == topY) updateTopY();
                if (y == bottomY) updateBottomY();
            } else if (predHi == y) {
                hi[pred] = y - 1;
                if (y == topY) topY = y - 1;
            } else if (predLo == y) {
                lo[pred] = y + 1;
                if (y == bottomY) bottomY = y + 1;
            } else {
                hi[pred] = y - 1;
                int n = allocNode(y + 1, predHi);
                if (prio[pred] >= prio[n]) {
                    int parent = NULL, cur = right[pred];
                    while (cur != NULL && prio[cur] >= prio[n]) {
                        parent = cur;
                        cur = left[cur];
                    }
                    right[n] = cur;
                    left[n] = NULL;
                    if (parent == NULL) right[pred] = n;
                    else left[parent] = n;
                } else {
                    insertAllocated(n, y + 1);
                }
            }
        }
        return true;
    }

    // ---- Serialization ----------------------------------------------------

    /**
     * Writes the tree contents to {@code buf} as a count followed by (lo, hi) pairs in ascending order.
     * All values are written as VarInts.
     */
    public void writeData(CCPacketBuffer buf) {
        buf.writeVarIntToBuffer(nodeCount);
        if (nodeCount == 0) return;

        // In-order traversal via an explicit int[] stack to avoid recursion overhead
        int[] stack = new int[nodeCount + 1];
        int top = -1;
        int cur = root;
        while (cur != NULL || top >= 0) {
            while (cur != NULL) {
                stack[++top] = cur;
                cur = left[cur];
            }
            cur = stack[top--];
            buf.writeVarIntToBuffer(lo[cur]);
            buf.writeVarIntToBuffer(hi[cur]);
            cur = right[cur];
        }
    }

    /**
     * Reads tree contents written by {@link #writeData}. Resets all existing state first.
     */
    public void readData(CCPacketBuffer buf) {
        Arrays.fill(left, 0, capacity, NULL);
        Arrays.fill(right, 0, capacity, NULL);
        root = NULL;
        freeHead = NULL;
        nodeCount = 0;
        topY = Coords.NO_HEIGHT;
        bottomY = Coords.NO_HEIGHT;

        int count = buf.readVarIntFromBuffer();
        // Pairs arrive sorted, non-overlapping, non-adjacent: no merging occurs on insert
        for (int i = 0; i < count; i++) {
            int pairLo = buf.readVarIntFromBuffer();
            int pairHi = buf.readVarIntFromBuffer();
            insertInterval(pairLo, pairHi);
        }
        updateTopY();
        updateBottomY();
    }

    // ---- Node pool --------------------------------------------------------

    private int allocNode(int newLo, int newHi) {
        int n;
        if (freeHead != NULL) {
            n = freeHead;
            freeHead = left[n]; // advance free list
            left[n] = NULL;
            right[n] = NULL;
        } else {
            if (nodeCount == capacity) grow();
            n = nodeCount;
            // left[n] and right[n] are already NULL: constructor/grow() fill new slots with NULL
        }
        nodeCount++;
        lo[n] = newLo;
        hi[n] = newHi;
        prio[n] = rng.nextInt();
        return n;
    }

    private void freeNode(int n) {
        nodeCount--;
        left[n] = freeHead;
        freeHead = n;
    }

    private void grow() {
        int newCap = capacity * 2;
        lo = Arrays.copyOf(lo, newCap);
        hi = Arrays.copyOf(hi, newCap);
        left = Arrays.copyOf(left, newCap);
        right = Arrays.copyOf(right, newCap);
        prio = Arrays.copyOf(prio, newCap);
        Arrays.fill(left, capacity, newCap, NULL);
        Arrays.fill(right, capacity, newCap, NULL);
        capacity = newCap;
    }

    // ---- Treap core -------------------------------------------------------

    /**
     * Splits the subtree rooted at {@code node} into two treaps by key:
     * {@link #splitL} receives all nodes with {@code lo < key},
     * {@link #splitR} receives all nodes with {@code lo >= key}.
     * Child pointers are modified in-place.
     * Iterative to avoid method-call overhead and stack-frame allocation.
     */
    private void split(int node, int key) {
        splitL = NULL;
        splitR = NULL;
        int lTail = NULL; // rightmost node in the L chain; right[lTail] is the next slot to fill
        int rTail = NULL; // leftmost node in the R chain; left[rTail] is the next slot to fill
        while (node != NULL) {
            if (lo[node] < key) {
                if (lTail == NULL) splitL = node;
                else right[lTail] = node;
                lTail = node;
                node = right[node]; // descend; right[lTail] will be updated on next iter or at end
            } else {
                if (rTail == NULL) splitR = node;
                else left[rTail] = node;
                rTail = node;
                node = left[node];
            }
        }
        if (lTail != NULL) right[lTail] = NULL;
        if (rTail != NULL) left[rTail] = NULL;
    }

    /**
     * Merges two treaps where all lo-values in {@code L} are less than all lo-values in {@code R}.
     * Returns the root of the merged treap.
     * Iterative to avoid method-call overhead and stack-frame allocation.
     */
    private int merge(int L, int R) {
        if (L == NULL) return R;
        if (R == NULL) return L;
        // Unroll first iteration so the hot loop never needs a tail==NULL check.
        int root;
        int tail;
        boolean tailGoRight;
        if (prio[L] > prio[R]) {
            root = L;
            tail = L;
            tailGoRight = true;
            L = right[L];
        } else {
            root = R;
            tail = R;
            tailGoRight = false;
            R = left[R];
        }
        while (L != NULL && R != NULL) {
            int parent;
            boolean goRight;
            if (prio[L] > prio[R]) {
                parent = L;
                goRight = true;
                L = right[L];
            } else {
                parent = R;
                goRight = false;
                R = left[R];
            }
            if (tailGoRight) right[tail] = parent;
            else left[tail] = parent;
            tail = parent;
            tailGoRight = goRight;
        }
        int remaining = L != NULL ? L : R;
        if (tailGoRight) right[tail] = remaining;
        else left[tail] = remaining;
        return root;
    }

    /** Returns the node index with the maximum {@code lo <= y}, or NULL if none exists. */
    private int findPred(int y) {
        int node = root;
        int result = NULL;
        while (node != NULL) {
            if (lo[node] <= y) {
                result = node;
                node = right[node];
            } else {
                node = left[node];
            }
        }
        return result;
    }

    private void insertInterval(int newLo, int newHi) {
        insertAllocated(allocNode(newLo, newHi), newLo);
    }

    /**
     * Inserts pre-allocated node {@code n} (with {@code lo[n]} already set to {@code newLo})
     * into the tree using a walk-down + split, exactly as insertInterval does — but skips
     * the allocNode call so callers can pre-check priority before deciding the insertion path.
     */
    private void insertAllocated(int n, int newLo) {
        int nPrio = prio[n];
        int parent = NULL;
        boolean isRight = false;
        int cur = root;
        while (cur != NULL && prio[cur] >= nPrio) {
            if (lo[cur] < newLo) {
                parent = cur;
                isRight = true;
                cur = right[cur];
            } else {
                parent = cur;
                isRight = false;
                cur = left[cur];
            }
        }
        split(cur, newLo);
        left[n] = splitL;
        right[n] = splitR;
        if (parent == NULL) root = n;
        else if (isRight) right[parent] = n;
        else left[parent] = n;
    }

    private void deleteInterval(int loKey) {
        // Walk to the unique node whose lo == loKey, then splice it out by merging its children.
        // Saves one split vs the split/split/merge approach.
        int parent = NULL;
        boolean isRight = false;
        int cur = root;
        while (cur != NULL) {
            int curLo = lo[cur];
            if (curLo == loKey) {
                int merged = merge(left[cur], right[cur]);
                freeNode(cur);
                if (parent == NULL) root = merged;
                else if (isRight) right[parent] = merged;
                else left[parent] = merged;
                return;
            } else if (curLo < loKey) {
                parent = cur;
                isRight = true;
                cur = right[cur];
            } else {
                parent = cur;
                isRight = false;
                cur = left[cur];
            }
        }
    }

    private void updateTopY() {
        if (root == NULL) {
            topY = Coords.NO_HEIGHT;
            return;
        }
        int node = root;
        while (right[node] != NULL) {
            node = right[node];
        }
        topY = hi[node];
    }

    private void updateBottomY() {
        if (root == NULL) {
            bottomY = Coords.NO_HEIGHT;
            return;
        }
        int node = root;
        while (left[node] != NULL) {
            node = left[node];
        }
        bottomY = lo[node];
    }
}
