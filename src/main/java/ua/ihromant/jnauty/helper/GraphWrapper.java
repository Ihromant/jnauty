package ua.ihromant.jnauty.helper;

public interface GraphWrapper {
    int size();

    int colors();

    int color(int idx);

    boolean edge(int a, int b);

    static GraphWrapper forFull(Liner liner) {
        return new GraphWrapper() {
            @Override
            public int size() {
                return liner.pointCount() + liner.lineCount();
            }

            @Override
            public int colors() {
                return 2;
            }

            @Override
            public int color(int idx) {
                return idx < liner.pointCount() ? 0 : 1;
            }

            @Override
            public boolean edge(int a, int b) {
                int pc = liner.pointCount();
                if (a < pc) {
                    return b >= pc && liner.flag(b - pc, a);
                } else {
                    return b < pc && liner.flag(a - pc, b);
                }
            }
        };
    }
}