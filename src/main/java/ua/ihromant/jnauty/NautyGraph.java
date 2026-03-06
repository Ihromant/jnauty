package ua.ihromant.jnauty;

public interface NautyGraph {
    int vCount();

    default int vColor(int idx) {
        return 0;
    }

    boolean edge(int a, int b);

    default int eCount() {
        return 0;
    }

    default long[] neighborsArr(int i) {
        int sz = vCount();
        long[] res = new long[(sz + 63) >>> 6];
        for (int j = 0; j < sz; j++) {
            if (i != j && edge(i, j)) {
                int word = j >>> 6;
                res[word] |= (1L << j);
            }
        }
        return res;
    }
}