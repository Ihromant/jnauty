package ua.ihromant.jnauty;

import java.util.Arrays;

public record GraphData(int[][] autGens, int[] orbits, long autCount, int[] labeling, long[] canonical) {
    public boolean isomorphic(GraphData that) {
        return this.labeling.length == that.labeling.length && Arrays.equals(this.canonical, that.canonical);
    }

    public int[] isomorphism(GraphData that) {
        if (!isomorphic(that)) {
            return null;
        }
        int len = this.labeling.length;
        int[] rev = new int[len];
        for (int i = 0; i < len; i++) {
            rev[this.labeling[i]] = i;
        }
        int[] result = new int[len];
        for (int i = 0; i < len; i++) {
            result[i] = that.labeling[rev[i]];
        }
        return result;
    }
}
