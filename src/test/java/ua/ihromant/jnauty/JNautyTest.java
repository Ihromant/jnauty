package ua.ihromant.jnauty;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class JNautyTest {
    private static final String FANO = """
            1110000
            1001100
            0101010
            1000011
            0011001
            0100101
            0010110
            """;

    @Test
    public void testAutNoE() {
        boolean[][] inc = new boolean[7][7];
        String[] lns = FANO.lines().toArray(String[]::new);
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                inc[i][j] = lns[j].charAt(i) == '1';
            }
        }
        NautyGraph gw = new PlaneGW(inc);
        GraphData autNauty = JNauty.instance().nauty(gw);
        GraphData autTraces = JNauty.instance().traces(gw);
        GraphData autSparse = JNauty.instance().sparseNauty(gw);
        assertEquals(168, autNauty.autCount());
        assertEquals(168, autTraces.autCount());
        assertEquals(168, autSparse.autCount());
        for (int i = 0; i < 1000; i++) {
            boolean[][] permuted = randomPermutation(inc);
            NautyGraph altGW = new PlaneGW(permuted);
            GraphData altNauty = JNauty.instance().nauty(altGW);
            GraphData altTraces = JNauty.instance().traces(altGW);
            GraphData altSparse = JNauty.instance().sparseNauty(altGW);
            testGraphData(altNauty, altGW, autNauty, gw, permuted);
            testGraphData(altTraces, altGW, autTraces, gw, permuted);
            testGraphData(altSparse, altGW, autSparse, gw, permuted);
        }
    }

    @Test
    public void testAut() {
        boolean[][] inc = new boolean[7][7];
        String[] lns = FANO.lines().toArray(String[]::new);
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                inc[i][j] = lns[j].charAt(i) == '1';
            }
        }
        NautyGraph gw = new PlaneGWE(inc);
        GraphData autNauty = JNauty.instance().nauty(gw);
        GraphData autTraces = JNauty.instance().traces(gw);
        GraphData autSparse = JNauty.instance().sparseNauty(gw);
        assertEquals(168, autNauty.autCount());
        assertEquals(168, autTraces.autCount());
        assertEquals(168, autSparse.autCount());
        for (int i = 0; i < 1000; i++) {
            boolean[][] permuted = randomPermutation(inc);
            NautyGraph altGW = new PlaneGWE(permuted);
            GraphData altNauty = JNauty.instance().nauty(altGW);
            GraphData altTraces = JNauty.instance().traces(altGW);
            GraphData altSparse = JNauty.instance().sparseNauty(altGW);
            testGraphData(altNauty, altGW, autNauty, gw, permuted);
            testGraphData(altTraces, altGW, autTraces, gw, permuted);
            testGraphData(altSparse, altGW, autSparse, gw, permuted);
        }
    }

    private static void testGraphData(GraphData permutedData, NautyGraph altGW, GraphData originalData, NautyGraph gw, boolean[][] permuted) {
        int[] lab = permutedData.labeling();
        boolean[][] byLabeling = applyLabeling(altGW, lab);
        boolean[][] byCanon = canonToIncidence(altGW.vCount(), permutedData.canonical());
        assertArrayEquals(byLabeling, byCanon);
        assertArrayEquals(originalData.canonical(), permutedData.canonical());
        int[] isomorphism = originalData.isomorphism(permutedData);
        int[] revIso = permutedData.isomorphism(originalData);
        assertNotNull(isomorphism);
        assertNotNull(revIso);
        for (int j = 0; j < gw.vCount(); j++) {
            for (int k = 0; k < gw.vCount(); k++) {
                assertEquals(altGW.edge(isomorphism[j], isomorphism[k]), gw.edge(j, k));
                assertEquals(gw.edge(revIso[j], revIso[k]), altGW.edge(j, k));
            }
        }
        int[][] automorphisms = permutedData.automorphisms();
        assertEquals(permutedData.autCount(), automorphisms.length);
        for (int[] a : automorphisms) {
            boolean[][] automorphed = new boolean[7][7];
            for (int j = 0; j < 7; j++) {
                for (int k = 0; k < 7; k++) {
                    automorphed[a[j + 7] - 7][a[k]] = permuted[j][k];
                }
            }
            assertArrayEquals(automorphed, permuted);
        }
    }

    private static boolean[][] applyLabeling(NautyGraph gr, int[] labeling) {
        int sz = gr.vCount();
        boolean[][] result = new boolean[sz][sz];
        for (int i = 0; i < sz; i++) {
            for (int j = 0; j < sz; j++) {
                result[i][j] = gr.edge(labeling[i], labeling[j]);
            }
        }
        return result;
    }

    private static boolean[][] canonToIncidence(int size, long[] canon) {
        boolean[][] result = new boolean[size][size];
        int m = (size + Long.SIZE - 1) / Long.SIZE;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < m; j++) {
                long packed = canon[i * m + j];
                for (int k = 0; k < Long.SIZE; k++) {
                    int l = j * Long.SIZE + k;
                    if (l >= size) {
                        continue;
                    }
                    boolean edge = (packed & (1L << k)) != 0;
                    result[i][l] = edge;
                }
            }
        }
        return result;
    }

    private static boolean[][] randomPermutation(boolean[][] inc) {
        int[] horPerm = PERM7[ThreadLocalRandom.current().nextInt(PERM7.length)];
        int[] verPerm = PERM7[ThreadLocalRandom.current().nextInt(PERM7.length)];
        boolean[][] result = new boolean[inc.length][inc[0].length];
        for (int i = 0; i < inc.length; i++) {
            for (int j = 0; j < inc[i].length; j++) {
                result[i][j] = inc[horPerm[i]][verPerm[j]];
            }
        }
        return result;
    }

    private static final int[][] PERM7 = permutations(IntStream.range(0, 7).toArray()).toArray(int[][]::new);

    private static Stream<int[]> permutations(int[] base) {
        int cnt = base.length;
        if (cnt < 2) {
            return Stream.of(base);
        }
        return Stream.iterate(base, Objects::nonNull, JNautyTest::nextPermutation);
    }

    private static int[] nextPermutation(int[] prev) {
        int[] next = prev.clone();
        int last = next.length - 2;
        while (last >= 0) {
            if (next[last] < next[last + 1]) {
                break;
            }
            last--;
        }
        if (last < 0) {
            return null;
        }

        int nextGreater = next.length - 1;
        for (int i = next.length - 1; i > last; i--) {
            if (next[i] > next[last]) {
                nextGreater = i;
                break;
            }
        }

        int temp = next[nextGreater];
        next[nextGreater] = next[last];
        next[last] = temp;

        int left = last + 1;
        int right = next.length - 1;
        while (left < right) {
            int temp1 = next[left];
            next[left++] = next[right];
            next[right--] = temp1;
        }

        return next;
    }

    private record PlaneGW(boolean[][] inc) implements NautyGraph {
        @Override
        public int vCount() {
            return inc[0].length + inc.length;
        }

        @Override
        public int vColor(int idx) {
            return idx < inc[0].length ? 0 : 1;
        }

        @Override
        public boolean edge(int a, int b) {
            int pc = inc[0].length;
            if (a < pc) {
                return b >= pc && inc[b - pc][a];
            } else {
                return b < pc && inc[a - pc][b];
            }
        }
    }

    private record PlaneGWE(boolean[][] inc) implements NautyGraph {
        @Override
        public int vCount() {
            return inc[0].length + inc.length;
        }

        @Override
        public int vColor(int idx) {
            return idx < inc[0].length ? 0 : 1;
        }

        @Override
        public boolean edge(int a, int b) {
            int pc = inc[0].length;
            if (a < pc) {
                return b >= pc && inc[b - pc][a];
            } else {
                return b < pc && inc[a - pc][b];
            }
        }

        @Override
        public int eCount() {
            int res = 0;
            for (boolean[] arr : inc) {
                for (boolean b : arr) {
                    if (b) {
                        res++;
                    }
                }
            }
            return 2 * res;
        }
    }

    @Test
    public void testLarge() throws URISyntaxException, IOException {
        String s = Files.readString(Path.of(Objects.requireNonNull(
                getClass().getResource("/S(2,7,175).txt")).toURI()));
        s.lines().parallel().forEach(ln -> {
            int sep = ln.indexOf('}') + 1;
            int[][] arr = new ObjectMapper().readValue(ln.substring(sep), int[][].class);
            int v = Arrays.stream(arr).mapToInt(a -> a[a.length - 1]).max().orElseThrow() + 1;
            boolean[][] inc = new boolean[arr.length][v];
            for (int l = 0; l < arr.length; l++) {
                for (int p : arr[l]) {
                    inc[l][p] = true;
                }
            }
            NautyGraph gw = new PlaneGWE(inc);
            GraphData autNauty = JNauty.instance().nauty(gw);
            assertEquals(504, autNauty.autCount());
            GraphData autTraces = JNauty.instance().traces(gw);
            assertEquals(504, autTraces.autCount());
            GraphData autSparse = JNauty.instance().sparseNauty(gw);
            assertEquals(504, autSparse.autCount());
        });
    }

    @Test
    public void testCliques() {
        for (int n = 4; n < 100; n++) {
            SparseGraph g = new SparseGraph();
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    g.connect(i, j);
                }
            }
            assertEquals(1, JNauty.instance().maximalCliques(g).size());
            SparseGraph g1 = new SparseGraph();
            for (int i = 0; i < n; i++) {
                g1.connect(i, (i + 1) % n);
            }
            assertEquals(n, JNauty.instance().maximalCliques(g1).size());
        }
        int[][] arr = new int[][] {
                {1, 2, 3},
                {0, 2, 3, 4},
                {0, 1, 3, 4},
                {0, 1, 2, 5},
                {1, 2, 6},
                {3, 6, 7},
                {4, 5, 7, 8},
                {5, 6, 8},
                {6, 7, 9},
                {8, 10, 11},
                {9, 11},
                {9, 10}
        };
        SparseGraph g = new SparseGraph();
        for (int i = 0; i < arr.length; i++) {
            for (int j : arr[i]) {
                g.connect(i, j);
            }
        }
        assertEquals(1, JNauty.instance().maximalCliques(g).size());
        checkCliques(new int[][]{{0, 1, 2, 3}}, JNauty.instance().maximalCliques(g, 4));
        checkCliques(new int[][]{{1, 2, 4}, {5, 6, 7}, {6, 7, 8}, {9, 10, 11}}, JNauty.instance().maximalCliques(g, 3));
        checkCliques(new int[][]{{3, 5}, {4, 6}, {8, 9}}, JNauty.instance().maximalCliques(g, 2));

        SparseGraph g2 = new SparseGraph();
        g2.connect(0, 1);
        g2.connect(0, 4);
        g2.connect(1, 4);
        g2.connect(1, 2);
        g2.connect(3, 4);
        g2.connect(2, 3);
        g2.connect(3, 5);
        assertEquals(1, JNauty.instance().maximalCliques(g2).size());
        checkCliques(new int[][]{{0, 1, 4}}, JNauty.instance().maximalCliques(g2, 3));
        checkCliques(new int[][]{{1, 2}, {2, 3}, {3, 4}, {3, 5}}, JNauty.instance().maximalCliques(g2, 2));
    }

    private static BitSet bs(int[] arr) {
        BitSet bs = new BitSet();
        for (int i : arr) {
            bs.set(i);
        }
        return bs;
    }

    private void checkCliques(int[][] expected, List<long[]> actual) {
        assertEquals(Arrays.stream(expected).map(JNautyTest::bs).collect(Collectors.toSet()),
                actual.stream().map(BitSet::valueOf).collect(Collectors.toSet()));
    }

    @Test
    public void testLargeGraph() throws URISyntaxException, IOException {
        SparseGraph sg = new SparseGraph();
        List<String> lines = Files.readAllLines(Path.of(Objects.requireNonNull(getClass().getResource("/graph.txt")).toURI()));
        for (int i = 0; i < lines.size(); i++) {
            String[] spl = lines.get(i).split(" ");
            for (String s : spl) {
                sg.connect(i, Integer.parseInt(s));
            }
        }
        AtomicInteger counter = new AtomicInteger();
        JNauty.instance().maximalCliques(sg, 0, _ -> counter.incrementAndGet());
        assertEquals(312498812, counter.get());
    }

    private static class SparseGraph implements NautyGraph {
        private final List<Set<Integer>> neighbors = new ArrayList<>();

        public void connect(int a, int b) {
            while (neighbors.size() <= Math.max(a, b)) {
                neighbors.add(new HashSet<>());
            }
            neighbors.get(a).add(b);
            neighbors.get(b).add(a);
        }

        @Override
        public int vCount() {
            return neighbors.size();
        }

        @Override
        public boolean edge(int a, int b) {
            return neighbors.get(a).contains(b);
        }
    }
}
