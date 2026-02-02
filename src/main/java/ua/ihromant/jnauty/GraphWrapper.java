package ua.ihromant.jnauty;

public interface GraphWrapper {
    int size();

    default int color(int idx) {
        return 0;
    }

    boolean edge(int a, int b);
}