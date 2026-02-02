package ua.ihromant.jnauty;

import org.junit.jupiter.api.Test;

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
        GraphWrapper gw = new PlaneGW(inc);
        Automorphisms aut = JNauty.instance().automorphisms(gw);
        assertEquals(168, aut.count());
        for (int i = 0; i < 1000; i++) {
            GraphWrapper altGW = new PlaneGW(randomPermutation(inc));
            Automorphisms altAut = JNauty.instance().automorphisms(altGW);
            assertArrayEquals(aut.canonical(), altAut.canonical());
        }
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

    private record PlaneGW(boolean[][] inc) implements GraphWrapper {
        @Override
        public int size() {
            return inc[0].length + inc.length;
        }

        @Override
        public int color(int idx) {
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
}
