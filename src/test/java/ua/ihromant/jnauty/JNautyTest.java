package ua.ihromant.jnauty;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    public void testAuth() {
        boolean[][] inc = new boolean[7][7];
        String[] lns = FANO.lines().toArray(String[]::new);
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                inc[i][j] = lns[j].charAt(i) == '1';
            }
        }
        NautyGraph gw = new PlaneGW(inc);
        GraphData aut = JNauty.instance().automorphisms(gw);
        assertEquals(168, aut.autCount());
        for (int i = 0; i < 1000; i++) {
            NautyGraph altGW = new PlaneGW(randomPermutation(inc));
            GraphData altAut = JNauty.instance().automorphisms(altGW);
            int[] lab = altAut.labeling();
            boolean[][] byLabeling = applyLabeling(altGW, lab);
            boolean[][] byCanon = canonToIncidence(altGW.vCount(), altAut.canonical());
            assertArrayEquals(byLabeling, byCanon);
            assertArrayEquals(aut.canonical(), altAut.canonical());
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
            NautyGraph gw = new PlaneGW(inc);
            GraphData aut = JNauty.instance().automorphisms(gw);
            assertEquals(504, aut.autCount());
        });
    }
}
