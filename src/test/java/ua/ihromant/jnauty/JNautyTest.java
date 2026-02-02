package ua.ihromant.jnauty;

import org.junit.jupiter.api.Test;

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
        assertEquals(168, JNauty.instance().automorphisms(gw).count());
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
