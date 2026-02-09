package ua.ihromant.jnauty;

import ua.ihromant.jnauty.ffm.NautyTraces_1;
import ua.ihromant.jnauty.ffm.TracesOptions;
import ua.ihromant.jnauty.ffm.TracesStats;
import ua.ihromant.jnauty.ffm.optionstruct;
import ua.ihromant.jnauty.ffm.sparsegraph;
import ua.ihromant.jnauty.ffm.statsblk;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JNauty {
    private static final int NAUTY_FALSE = 0;
    private static final int NAUTY_TRUE = 1;
    private static final JNauty INSTANCE = new JNauty();

    public static JNauty instance() {
        return INSTANCE;
    }

    private JNauty() {
        try {
            Path soFile = Paths.get(System.getProperty("java.io.tmpdir"), "nauty-" + UUID.randomUUID() + ".so");
            Files.copy(Objects.requireNonNull(JNauty.class.getResourceAsStream("/libnauty.so")),
                    soFile);
            System.load(soFile.toString());
            soFile.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public GraphData nauty(NautyGraph gw) {
        int sz = gw.vCount();
        List<int[]> gens = new ArrayList<>();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment options = arena.allocate(optionstruct.layout());

            // defaults
            optionstruct.getcanon(options, NAUTY_TRUE);
            optionstruct.digraph(options, NAUTY_FALSE);
            optionstruct.writeautoms(options, NAUTY_FALSE);
            optionstruct.writemarkers(options, NAUTY_FALSE);
            optionstruct.defaultptn(options, NAUTY_TRUE);
            optionstruct.cartesian(options, NAUTY_FALSE);
            optionstruct.linelength(options, 78);
            optionstruct.outfile(options, MemorySegment.NULL);
            optionstruct.userrefproc(options, MemorySegment.NULL);

            MemorySegment automProc = optionstruct.userautomproc.allocate((_, p, _, _, _, _) -> {
                int[] arr = p.asSlice(0, (long) Integer.BYTES * gw.vCount()).toArray(ValueLayout.JAVA_INT);
                gens.add(arr);
            }, arena);

            optionstruct.userautomproc(options, automProc);
            optionstruct.userlevelproc(options, MemorySegment.NULL);
            optionstruct.usernodeproc(options, MemorySegment.NULL);
            optionstruct.usercanonproc(options, MemorySegment.NULL);
            optionstruct.invarproc(options, MemorySegment.NULL);
            optionstruct.tc_level(options, 100);
            optionstruct.mininvarlevel(options, 0);
            optionstruct.maxinvarlevel(options, 1);
            optionstruct.invararg(options, 0);
            optionstruct.dispatch(options, NautyTraces_1.dispatch_graph());
            optionstruct.schreier(options, NAUTY_FALSE);
            optionstruct.extra_options(options, MemorySegment.NULL);

            // update defaults
            optionstruct.defaultptn(options, NAUTY_FALSE);
            optionstruct.invarproc(options, NautyTraces_1.adjtriang.ADDR);
            optionstruct.mininvarlevel(options, 1);
            optionstruct.maxinvarlevel(options, 2);
            optionstruct.tc_level(options, 10);

            int rowSize = (sz + Long.SIZE - 1) / Long.SIZE;
            long[] g = new long[rowSize * sz];
            int sh = 0;
            for (int i = 0; i < sz; i++) {
                long word = 0L;
                int bit = 63;          // MSB → LSB
                int out = sh;          // index into g for this row

                for (int j = 0; j < sz; j++) {
                    if (gw.edge(i, j)) {
                        word |= (1L << bit);
                    }

                    bit--;

                    if (bit < 0) {     // word full
                        g[out++] = word;
                        word = 0L;
                        bit = 63;
                    }
                }

                // flush partial word (if sz not multiple of 64)
                if (bit != 63) {
                    g[out] = word;
                }

                sh += rowSize;
            }
            int[] lab = new int[sz];
            int[] ptn = new int[sz];
            Map<Integer, List<Integer>> grouped = IntStream.range(0, sz).boxed()
                    .collect(Collectors.groupingBy(gw::vColor));
            int[] colorSet = grouped.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
            int cnt = 0;
            for (int color : colorSet) {
                List<Integer> ints = grouped.get(color);
                for (int j = 0; j < ints.size() - 1; j++) {
                    lab[cnt] = ints.get(j);
                    ptn[cnt++] = 1;
                }
                lab[cnt++] = ints.getLast();
            }
            MemorySegment stats = arena.allocate(statsblk.layout());
            MemorySegment nativeG = arena.allocate(ValueLayout.JAVA_LONG, g.length);
            nativeG.copyFrom(MemorySegment.ofArray(g));
            MemorySegment nativeLab = arena.allocate(ValueLayout.JAVA_INT, lab.length);
            nativeLab.copyFrom(MemorySegment.ofArray(lab));
            MemorySegment nativePtn = arena.allocate(ValueLayout.JAVA_INT, ptn.length);
            nativePtn.copyFrom(MemorySegment.ofArray(ptn));
            MemorySegment nativeOrbits = arena.allocate(ValueLayout.JAVA_INT, sz);
            MemorySegment canon = arena.allocate(ValueLayout.JAVA_LONG, g.length);
            NautyTraces_1.densenauty(nativeG, nativeLab, nativePtn,
                    nativeOrbits, options, stats,
                    rowSize, sz, canon);
            return new GraphData(gens.toArray(int[][]::new),
                    nativeOrbits.toArray(ValueLayout.JAVA_INT),
                    (long) statsblk.grpsize1(stats),
                    nativeLab.toArray(ValueLayout.JAVA_INT),
                    Arrays.stream(canon.toArray(ValueLayout.JAVA_LONG)).map(Long::reverse).toArray());
        }
    }

    public GraphData traces(NautyGraph gw) {
        int sz = gw.vCount();
        List<int[]> gens = new ArrayList<>();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment options = arena.allocate(TracesOptions.layout());

            // defaults
            TracesOptions.getcanon(options, NAUTY_FALSE);
            TracesOptions.writeautoms(options, NAUTY_FALSE);
            TracesOptions.cartesian(options, NAUTY_FALSE);
            TracesOptions.digraph(options, NAUTY_FALSE);
            TracesOptions.digraph(options, NAUTY_TRUE);
            TracesOptions.linelength(options, 0);
            TracesOptions.outfile(options, MemorySegment.NULL);
            TracesOptions.strategy(options, 0);
            TracesOptions.verbosity(options, 0);
            TracesOptions.generators(options, MemorySegment.NULL);
            TracesOptions.userautomproc(options, MemorySegment.NULL);
            TracesOptions.reserved(options, MemorySegment.NULL);
            TracesOptions.weighted(options, NAUTY_FALSE);

            // override defaults
            TracesOptions.defaultptn(options, NAUTY_FALSE);
            TracesOptions.getcanon(options, NAUTY_TRUE);
            MemorySegment automProc = TracesOptions.userautomproc.allocate((int _, MemorySegment p, int n) -> {
                int[] arr = p.asSlice(0, (long) Integer.BYTES * n).toArray(ValueLayout.JAVA_INT);
                gens.add(arr);
            }, arena);
            TracesOptions.userautomproc(options, automProc);

            int rowSize = (sz + Long.SIZE - 1) / Long.SIZE;
            long[] g = new long[rowSize * sz];
            int sh = 0;
            for (int i = 0; i < sz; i++) {
                long word = 0L;
                int bit = 63;          // MSB → LSB
                int out = sh;          // index into g for this row

                for (int j = 0; j < sz; j++) {
                    if (gw.edge(i, j)) {
                        word |= (1L << bit);
                    }

                    bit--;

                    if (bit < 0) {     // word full
                        g[out++] = word;
                        word = 0L;
                        bit = 63;
                    }
                }

                // flush partial word (if sz not multiple of 64)
                if (bit != 63) {
                    g[out] = word;
                }

                sh += rowSize;
            }
            int[] lab = new int[sz];
            int[] ptn = new int[sz];
            Map<Integer, List<Integer>> grouped = IntStream.range(0, sz).boxed()
                    .collect(Collectors.groupingBy(gw::vColor));
            int[] colorSet = grouped.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
            int cnt = 0;
            for (int color : colorSet) {
                List<Integer> ints = grouped.get(color);
                for (int j = 0; j < ints.size() - 1; j++) {
                    lab[cnt] = ints.get(j);
                    ptn[cnt++] = 1;
                }
                lab[cnt++] = ints.getLast();
            }

            MemorySegment nativeG = arena.allocate(ValueLayout.JAVA_LONG, g.length);
            nativeG.copyFrom(MemorySegment.ofArray(g));
            MemorySegment sparseGraph = NautyTraces_1.nauty_to_sg(nativeG, MemorySegment.NULL, rowSize, sz);
            MemorySegment stats = arena.allocate(TracesStats.layout());
            MemorySegment nativeLab = arena.allocate(ValueLayout.JAVA_INT, lab.length);
            nativeLab.copyFrom(MemorySegment.ofArray(lab));
            MemorySegment nativePtn = arena.allocate(ValueLayout.JAVA_INT, ptn.length);
            nativePtn.copyFrom(MemorySegment.ofArray(ptn));
            MemorySegment nativeOrbits = arena.allocate(ValueLayout.JAVA_INT, sz);
            MemorySegment sparseCanon = arena.allocate(sparsegraph.layout());
            NautyTraces_1.Traces(sparseGraph, nativeLab, nativePtn,
                    nativeOrbits, options, stats, sparseCanon);
            MemorySegment mc = arena.allocate(ValueLayout.JAVA_INT);
            MemorySegment canon = arena.allocate(ValueLayout.JAVA_LONG, g.length);
            NautyTraces_1.sg_to_nauty(sparseCanon, canon, rowSize, mc);
            return new GraphData(gens.toArray(int[][]::new),
                    nativeOrbits.toArray(ValueLayout.JAVA_INT),
                    (long) TracesStats.grpsize1(stats),
                    nativeLab.toArray(ValueLayout.JAVA_INT),
                    Arrays.stream(canon.asSlice(0, (long) Long.BYTES * g.length)
                            .toArray(ValueLayout.JAVA_LONG)).map(Long::reverse).toArray());
        }
    }
}
