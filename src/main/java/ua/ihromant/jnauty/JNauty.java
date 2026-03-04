package ua.ihromant.jnauty;

import ua.ihromant.jnauty.ffm.NautyTraces_1;
import ua.ihromant.jnauty.ffm.TracesOptions;
import ua.ihromant.jnauty.ffm.TracesStats;
import ua.ihromant.jnauty.ffm._clique_options;
import ua.ihromant.jnauty.ffm._graph_t;
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
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public class JNauty {
    private static final int NAUTY_FALSE = 0;
    private static final int NAUTY_TRUE = 1;
    private static final JNauty INSTANCE = new JNauty();

    private static final ThreadLocal<NautyAutom> na = ThreadLocal.withInitial(NautyAutom::new);
    private static final ThreadLocal<TracesAutom> ta = ThreadLocal.withInitial(TracesAutom::new);
    private static final ThreadLocal<CliqueUF> cu = ThreadLocal.withInitial(CliqueUF::new);

    public static JNauty instance() {
        return INSTANCE;
    }

    private JNauty() {
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            Path soFile = Paths.get(System.getProperty("java.io.tmpdir"), "nauty-" + UUID.randomUUID() + ".so");
            Files.copy(Objects.requireNonNull(JNauty.class.getResourceAsStream("/libnauty_" + osName + ".so")),
                    soFile);
            System.load(soFile.toString());
            if ("OpenBSD".equalsIgnoreCase(osName)) {
                System.load("/usr/lib/libc.so.102.0");
            }
            soFile.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public GraphData nauty(NautyGraph gw) {
        int sz = gw.vCount();
        List<int[]> gens = new ArrayList<>();
        NautyAutom nautyAutom = na.get();
        nautyAutom.setCons(gens::add);
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

            optionstruct.userautomproc(options, MemorySegment.NULL);
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
            optionstruct.userautomproc(options, nautyAutom.segm);
            optionstruct.defaultptn(options, NAUTY_FALSE);
            optionstruct.invarproc(options, NautyTraces_1.twopaths$address());
            optionstruct.mininvarlevel(options, 1);
            optionstruct.maxinvarlevel(options, 2);
            optionstruct.tc_level(options, 10);

            int rowSize = (sz + 63) >>> 6;
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
            List<int[]> grouped = new ArrayList<>();
            for (int i = 0; i < sz; i++) {
                int clr = gw.vColor(i);
                while (grouped.size() <= clr) {
                    grouped.add(new int[sz + 1]);
                }
                int[] arr = grouped.get(clr);
                arr[++arr[0]] = i;
            }
            int cnt = 0;
            for (int[] arr : grouped) {
                int s = arr[0];
                if (s == 0) {
                    continue;
                }
                for (int j = 1; j < s; j++) {
                    lab[cnt] = arr[j];
                    ptn[cnt++] = 1;
                }
                lab[cnt++] = arr[s];
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

    public GraphData sparseNauty(NautyGraph gw) {
        int sz = gw.vCount();
        List<int[]> gens = new ArrayList<>();
        NautyAutom nautyAutom = na.get();
        nautyAutom.setCons(gens::add);
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

            optionstruct.userautomproc(options, MemorySegment.NULL);
            optionstruct.userlevelproc(options, MemorySegment.NULL);
            optionstruct.usernodeproc(options, MemorySegment.NULL);
            optionstruct.usercanonproc(options, MemorySegment.NULL);
            optionstruct.invarproc(options, MemorySegment.NULL);
            optionstruct.tc_level(options, 100);
            optionstruct.mininvarlevel(options, 0);
            optionstruct.maxinvarlevel(options, 1);
            optionstruct.invararg(options, 0);
            optionstruct.dispatch(options, NautyTraces_1.dispatch_sparse());
            optionstruct.schreier(options, NAUTY_FALSE);
            optionstruct.extra_options(options, MemorySegment.NULL);

            // update defaults
            optionstruct.userautomproc(options, nautyAutom.segm);
            optionstruct.defaultptn(options, NAUTY_FALSE);
            optionstruct.tc_level(options, 10);

            int rowSize = (sz + 63) >>> 6;
            int canonSz = rowSize * sz;

            int ec = gw.eCount();
            MemorySegment sparseGraph = arena.allocate(sparsegraph.layout());
            MemorySegment nativeV = arena.allocate(ValueLayout.JAVA_LONG, sz);
            MemorySegment nativeD = arena.allocate(ValueLayout.JAVA_INT, sz);
            MemorySegment nativeE;
            if (ec == 0) {
                List<Integer> e = new ArrayList<>();
                int prev = 0;
                int od = 0;
                for (int i = 0; i < sz; i++) {
                    prev = prev + od;
                    od = 0;
                    for (int j = 0; j < sz; j++) {
                        if (gw.edge(i, j)) {
                            e.add(j);
                            od++;
                        }
                    }
                    nativeD.setAtIndex(ValueLayout.JAVA_INT, i, od);
                    nativeV.setAtIndex(ValueLayout.JAVA_LONG, i, prev);
                }
                ec = e.size();
                nativeE = arena.allocate(ValueLayout.JAVA_INT, ec);
                nativeE.copyFrom(MemorySegment.ofArray(e.stream().mapToInt(Integer::intValue).toArray()));
            } else {
                nativeE = arena.allocate(ValueLayout.JAVA_INT, ec);
                int cnt = 0;
                for (int i = 0; i < sz; i++) {
                    int prev = cnt;
                    for (int j = 0; j < sz; j++) {
                        if (gw.edge(i, j)) {
                            nativeE.setAtIndex(ValueLayout.JAVA_INT, cnt++, j);
                        }
                    }
                    nativeD.setAtIndex(ValueLayout.JAVA_INT, i, cnt - prev);
                    nativeV.setAtIndex(ValueLayout.JAVA_LONG, i, prev);
                }
            }

            sparsegraph.nde(sparseGraph, ec);
            sparsegraph.v(sparseGraph, nativeV);
            sparsegraph.nv(sparseGraph, sz);
            sparsegraph.d(sparseGraph, nativeD);
            sparsegraph.e(sparseGraph, nativeE);

            MemorySegment nativeLab = arena.allocate(ValueLayout.JAVA_INT, sz);
            MemorySegment nativePtn = arena.allocate(ValueLayout.JAVA_INT, sz);
            List<int[]> grouped = new ArrayList<>();
            for (int i = 0; i < sz; i++) {
                int clr = gw.vColor(i);
                while (grouped.size() <= clr) {
                    grouped.add(new int[sz + 1]);
                }
                int[] arr = grouped.get(clr);
                arr[++arr[0]] = i;
            }
            int cnt = 0;
            for (int[] arr : grouped) {
                int s = arr[0];
                if (s == 0) {
                    continue;
                }
                for (int j = 1; j < s; j++) {
                    nativeLab.setAtIndex(ValueLayout.JAVA_INT, cnt, arr[j]);
                    nativePtn.setAtIndex(ValueLayout.JAVA_INT, cnt++, 1);
                }
                nativeLab.setAtIndex(ValueLayout.JAVA_INT, cnt++, arr[s]);
            }

            MemorySegment stats = arena.allocate(statsblk.layout());
            MemorySegment nativeOrbits = arena.allocate(ValueLayout.JAVA_INT, sz);
            MemorySegment sparseCanon = arena.allocate(sparsegraph.layout());
            NautyTraces_1.sparsenauty(sparseGraph, nativeLab, nativePtn,
                    nativeOrbits, options, stats, sparseCanon);

            long[] canonArr = new long[canonSz];
            MemorySegment dCanon = sparsegraph.d(sparseCanon);
            MemorySegment eCanon = sparsegraph.e(sparseCanon);
            int idx = 0;
            for (int i = 0; i < sz; i++) {
                int nj = dCanon.getAtIndex(ValueLayout.JAVA_INT, i);
                for (int n = 0; n < nj; n++) {
                    int j = eCanon.getAtIndex(ValueLayout.JAVA_INT, idx + n);
                    int word = j >>> 6;
                    canonArr[rowSize * i + word] |= (1L << j);
                }
                idx = idx + nj;
            }

            GraphData result = new GraphData(gens.toArray(int[][]::new),
                    nativeOrbits.toArray(ValueLayout.JAVA_INT),
                    (long) TracesStats.grpsize1(stats),
                    nativeLab.toArray(ValueLayout.JAVA_INT),
                    canonArr);
            freeSparse(sparseCanon);
            return result;
        }
    }

    public GraphData traces(NautyGraph gw) {
        int sz = gw.vCount();
        List<int[]> gens = new ArrayList<>();
        TracesAutom tracesAutom = ta.get();
        tracesAutom.setCons(gens::add);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment options = arena.allocate(TracesOptions.layout());

            // defaults
            TracesOptions.getcanon(options, NAUTY_FALSE);
            TracesOptions.writeautoms(options, NAUTY_FALSE);
            TracesOptions.cartesian(options, NAUTY_FALSE);
            TracesOptions.digraph(options, NAUTY_FALSE);
            TracesOptions.defaultptn(options, NAUTY_TRUE);
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
            TracesOptions.userautomproc(options, tracesAutom.segm);

            int rowSize = (sz + 63) >>> 6;
            int canonSz = rowSize * sz;

            int ec = gw.eCount();
            MemorySegment sparseGraph = arena.allocate(sparsegraph.layout());
            MemorySegment nativeV = arena.allocate(ValueLayout.JAVA_LONG, sz);
            MemorySegment nativeD = arena.allocate(ValueLayout.JAVA_INT, sz);
            MemorySegment nativeE;
            if (ec == 0) {
                List<Integer> e = new ArrayList<>();
                int prev = 0;
                int od = 0;
                for (int i = 0; i < sz; i++) {
                    prev = prev + od;
                    od = 0;
                    for (int j = 0; j < sz; j++) {
                        if (gw.edge(i, j)) {
                            e.add(j);
                            od++;
                        }
                    }
                    nativeD.setAtIndex(ValueLayout.JAVA_INT, i, od);
                    nativeV.setAtIndex(ValueLayout.JAVA_LONG, i, prev);
                }
                ec = e.size();
                nativeE = arena.allocate(ValueLayout.JAVA_INT, ec);
                nativeE.copyFrom(MemorySegment.ofArray(e.stream().mapToInt(Integer::intValue).toArray()));
            } else {
                nativeE = arena.allocate(ValueLayout.JAVA_INT, ec);
                int cnt = 0;
                for (int i = 0; i < sz; i++) {
                    int prev = cnt;
                    for (int j = 0; j < sz; j++) {
                        if (gw.edge(i, j)) {
                            nativeE.setAtIndex(ValueLayout.JAVA_INT, cnt++, j);
                        }
                    }
                    nativeD.setAtIndex(ValueLayout.JAVA_INT, i, cnt - prev);
                    nativeV.setAtIndex(ValueLayout.JAVA_LONG, i, prev);
                }
            }

            sparsegraph.nde(sparseGraph, ec);
            sparsegraph.v(sparseGraph, nativeV);
            sparsegraph.nv(sparseGraph, sz);
            sparsegraph.d(sparseGraph, nativeD);
            sparsegraph.e(sparseGraph, nativeE);

            MemorySegment nativeLab = arena.allocate(ValueLayout.JAVA_INT, sz);
            MemorySegment nativePtn = arena.allocate(ValueLayout.JAVA_INT, sz);
            List<int[]> grouped = new ArrayList<>();
            for (int i = 0; i < sz; i++) {
                int clr = gw.vColor(i);
                while (grouped.size() <= clr) {
                    grouped.add(new int[sz + 1]);
                }
                int[] arr = grouped.get(clr);
                arr[++arr[0]] = i;
            }
            int cnt = 0;
            for (int[] arr : grouped) {
                int s = arr[0];
                if (s == 0) {
                    continue;
                }
                for (int j = 1; j < s; j++) {
                    nativeLab.setAtIndex(ValueLayout.JAVA_INT, cnt, arr[j]);
                    nativePtn.setAtIndex(ValueLayout.JAVA_INT, cnt++, 1);
                }
                nativeLab.setAtIndex(ValueLayout.JAVA_INT, cnt++, arr[s]);
            }

            MemorySegment stats = arena.allocate(TracesStats.layout());
            MemorySegment nativeOrbits = arena.allocate(ValueLayout.JAVA_INT, sz);
            MemorySegment sparseCanon = arena.allocate(sparsegraph.layout());
            NautyTraces_1.Traces(sparseGraph, nativeLab, nativePtn,
                    nativeOrbits, options, stats, sparseCanon);

            long[] canonArr = new long[canonSz];
            MemorySegment dCanon = sparsegraph.d(sparseCanon);
            MemorySegment eCanon = sparsegraph.e(sparseCanon);
            int idx = 0;
            for (int i = 0; i < sz; i++) {
                int nj = dCanon.getAtIndex(ValueLayout.JAVA_INT, i);
                for (int n = 0; n < nj; n++) {
                    int j = eCanon.getAtIndex(ValueLayout.JAVA_INT, idx + n);
                    int word = j >>> 6;
                    canonArr[rowSize * i + word] |= (1L << j);
                }
                idx = idx + nj;
            }

            GraphData result = new GraphData(gens.toArray(int[][]::new),
                    nativeOrbits.toArray(ValueLayout.JAVA_INT),
                    (long) TracesStats.grpsize1(stats),
                    nativeLab.toArray(ValueLayout.JAVA_INT),
                    canonArr);
            freeSparse(sparseCanon);
            return result;
        }
    }

    public List<long[]> maximalCliques(NautyGraph gw) {
        int sz = gw.vCount();
        int rowSize = (sz + 63) >>> 6;
        List<long[]> result = new ArrayList<>();
        CliqueUF ufHolder = cu.get();
        ufHolder.setCons(result::add);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment edgesArray = arena.allocate(ValueLayout.ADDRESS, sz);

            long[][] gr = new long[sz][rowSize];
            for (int i = 0; i < sz; i++) {
                for (int j = i + 1; j < sz; j++) {
                    if (gw.edge(i, j)) {
                        int iWord = j >>> 6;
                        gr[i][iWord] |= (1L << j);
                        int jWord = i >>> 6;
                        gr[j][jWord] |= (1L << i);
                    }
                }
            }
            for (int i = 0; i < sz; i++) {
                MemorySegment ms = arena.allocate(ValueLayout.JAVA_LONG, rowSize);
                ms.copyFrom(MemorySegment.ofArray(gr[i]));
                edgesArray.setAtIndex(ValueLayout.ADDRESS, i, ms);
            }

            MemorySegment graph = arena.allocate(_graph_t.layout());
            _graph_t.n(graph, sz);
            _graph_t.edges(graph, edgesArray);
            MemorySegment weights = arena.allocate(ValueLayout.JAVA_INT, sz);
            for (int i = 0; i < sz; i++) {
                weights.setAtIndex(ValueLayout.JAVA_INT, i, 1);
            }
            _graph_t.weights(graph, weights);

            MemorySegment options = arena.allocate(_clique_options.layout());
            _clique_options.user_function(options, ufHolder.segm);
            NautyTraces_1.clique_find_all(graph, 0, 0, NAUTY_TRUE, options);
        }
        return result;
    }

    private static void freeSparse(MemorySegment sg) {
        MemorySegment v = sparsegraph.v(sg);
        if (!MemorySegment.NULL.equals(v)) {
            NautyTraces_1.free(v);
        }
        MemorySegment d = sparsegraph.d(sg);
        if (!MemorySegment.NULL.equals(d)) {
            NautyTraces_1.free(d);
        }
        MemorySegment e = sparsegraph.e(sg);
        if (!MemorySegment.NULL.equals(e)) {
            NautyTraces_1.free(e);
        }
        MemorySegment w = sparsegraph.w(sg);
        if (!MemorySegment.NULL.equals(w)) {
            NautyTraces_1.free(w);
        }
    }

    private static class NautyAutom {
        private final MemorySegment segm;
        private Consumer<int[]> cons = _ -> {};

        private NautyAutom() {
            this.segm = optionstruct.userautomproc.allocate((_, p, _, _, _, n) -> {
                int[] arr = p.asSlice(0, (long) Integer.BYTES * n).toArray(ValueLayout.JAVA_INT);
                cons.accept(arr);
            }, Arena.global());
        }

        private void setCons(Consumer<int[]> cons) {
            this.cons = cons;
        }
    }

    private static class TracesAutom {
        private final MemorySegment segm;
        private Consumer<int[]> cons = _ -> {};

        private TracesAutom() {
            this.segm = TracesOptions.userautomproc.allocate((int _, MemorySegment p, int n) -> {
                int[] arr = p.asSlice(0, (long) Integer.BYTES * n).toArray(ValueLayout.JAVA_INT);
                cons.accept(arr);
            }, Arena.global());
        }

        private void setCons(Consumer<int[]> cons) {
            this.cons = cons;
        }
    }

    private static class CliqueUF {
        private final MemorySegment segm;
        private Consumer<long[]> cons = _ -> {};

        private CliqueUF() {
            this.segm = _clique_options.user_function.allocate((set, gh, _) -> {
                cons.accept(set.asSlice(0, (long) Long.BYTES * ((_graph_t.n(gh) + 63) >>> 6)).toArray(ValueLayout.JAVA_LONG));
                return 1;
            }, Arena.global());
        }

        private void setCons(Consumer<long[]> cons) {
            this.cons = cons;
        }
    }
}
