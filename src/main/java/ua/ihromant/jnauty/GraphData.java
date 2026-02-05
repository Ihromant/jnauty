package ua.ihromant.jnauty;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

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

    public int[][] automorphisms() {
        Set<ArrWrap> result = new HashSet<>();
        result.add(new ArrWrap(IntStream.range(0, orbits.length).toArray()));
        boolean added;
        do {
            added = false;
            for (ArrWrap el : result.toArray(ArrWrap[]::new)) {
                for (int[] gen : autGens) {
                    ArrWrap xy = new ArrWrap(combine(gen, el.map));
                    ArrWrap yx = new ArrWrap(combine(el.map, gen));
                    added = result.add(xy) || added;
                    added = result.add(yx) || added;
                }
            }
        } while (added);
        return result.stream().map(ArrWrap::map).toArray(int[][]::new);
    }

    private static int[] combine(int[] a, int[] b) {
        int[] result = new int[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[b[i]];
        }
        return result;
    }

    private record ArrWrap(int[] map) {
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ArrWrap(int[] map1))) return false;
            return Arrays.equals(map, map1);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(map) >>> 1;
        }
    }
}
