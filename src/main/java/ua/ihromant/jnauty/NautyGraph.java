package ua.ihromant.jnauty;

public interface NautyGraph {
    int vCount();

    default int vColor(int idx) {
        return 0;
    }

    boolean edge(int a, int b);
}