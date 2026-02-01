package ua.ihromant.jnauty;

import ua.ihromant.jnauty.ffm.nautinv_h;
import ua.ihromant.jnauty.ffm.optionstruct;
import ua.ihromant.jnauty.ffm.statsblk;
import ua.ihromant.jnauty.helper.FixBS;
import ua.ihromant.jnauty.helper.GraphWrapper;
import ua.ihromant.jnauty.helper.Liner;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
    //DEFAULTOPTIONS_GRAPH(options) optionblk options = \
    //{0,FALSE,FALSE,FALSE,TRUE,FALSE,CONSOLWIDTH, \
    //    NULL,NULL,NULL,NULL,NULL,NULL,NULL,100,0,1,0,&dispatch_graph,FALSE,NULL}

//    typedef struct optionstruct
//    {
//        int getcanon;             /* make canong and canonlab? */ 0
//#define LABELONLY 2   /* new value UNIMPLEMENTED */
//        boolean digraph;          /* multiple edges or loops? */ FALSE
//        boolean writeautoms;      /* write automorphisms? */ FALSE
//        boolean writemarkers;     /* write stats on pts fixed, etc.? */ FALSE
//        boolean defaultptn;       /* set lab,ptn,active for single cell? */ TRUE
//        boolean cartesian;        /* use cartesian rep for writing automs? */ FALSE
//        int linelength;           /* max chars/line (excl. '\n') for output */ CONSOLWIDTH
//        FILE *outfile;            /* file for output, if any */ NULL
//    void (*userrefproc)       /* replacement for usual refine procedure */ NULL
//        (graph*,int*,int*,int,int*,int*,set*,int*,int,int);
//    void (*userautomproc)     /* procedure called for each automorphism */ NULL
//        (int,int*,int*,int,int,int);
//    void (*userlevelproc)     /* procedure called for each level */ NULL
//        (int*,int*,int,int*,statsblk*,int,int,int,int,int,int);
//    void (*usernodeproc)      /* procedure called for each node */ NULL
//        (graph*,int*,int*,int,int,int,int,int,int);
//    int  (*usercanonproc)     /* procedure called for better labellings */ NULL
//        (graph*,int*,graph*,unsigned long,int,int,int);
//    void (*invarproc)         /* procedure to compute vertex-invariant */ NULL
//        (graph*,int*,int*,int,int,int,int*,int,boolean,int,int);
//        int tc_level;             /* max level for smart target cell choosing */ 100
//        int mininvarlevel;        /* min level for invariant computation */ 0
//        int maxinvarlevel;        /* max level for invariant computation */ 1
//        int invararg;             /* value passed to (*invarproc)() */ 0
//        dispatchvec *dispatch;    /* vector of object-specific routines */ &dispatch_graph
//        boolean schreier;         /* use random schreier method */ FALSE
//        void *extra_options;      /* arbitrary extra options */ NULL
//#ifdef NAUTY_IN_MAGMA
//        boolean print_stats;      /* CAYLEY specfic - GYM Sep 1990 */
//        char *invarprocname;      /* Magma - no longer global sjc 1994 */
//        int lab_h;                /* Magma - no longer global sjc 1994 */
//        int ptn_h;                /* Magma - no longer global sjc 1994 */
//        int orbitset_h;           /* Magma - no longer global sjc 1994 */
//#endif
//    } optionblk;

    private static final int NAUTY_FALSE = 0;
    private static final int NAUTY_TRUE = 1;

    public static void main(String[] args) {
        //Liner liner = Liner.byDiffFamily(new int[][]{{0, 9, 13}, {0, 11, 18}, {0, 14, 17}});
        Liner liner = Liner.fano();
        //Liner liner = Liner.byDiffFamily(new int[][]{{0, 1, 4}, {0, 2, 7}});
        GraphWrapper gw = GraphWrapper.forFull(liner);

        int sz = gw.size();
        System.load("/home/ihromant/workspace/jnauty/src/main/resources/libnauty.so");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment options = arena.allocate(optionstruct.layout());

            // defaults
            optionstruct.getcanon(options, 0);
            optionstruct.digraph(options, NAUTY_FALSE);
            optionstruct.writeautoms(options, NAUTY_FALSE);
            optionstruct.writemarkers(options, NAUTY_FALSE);
            optionstruct.defaultptn(options, NAUTY_TRUE);
            optionstruct.cartesian(options, NAUTY_FALSE);
            optionstruct.linelength(options, 78);
            optionstruct.outfile(options, MemorySegment.NULL);
            optionstruct.userrefproc(options, MemorySegment.NULL);

            MemorySegment automProc = optionstruct.userautomproc.allocate((_x0, p, _x2, _x3, _x4, _x5) -> {
                int[] arr = p.asSlice(0, (long) Integer.BYTES * gw.size()).toArray(ValueLayout.JAVA_INT);
                System.out.println(Arrays.toString(arr));
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
            optionstruct.dispatch(options, nautinv_h.dispatch_graph());
            optionstruct.schreier(options, NAUTY_FALSE);
            optionstruct.extra_options(options, MemorySegment.NULL);

            // update defaults
            optionstruct.defaultptn(options, NAUTY_FALSE);
            optionstruct.invarproc(options, nautinv_h.adjtriang.ADDR);
            optionstruct.mininvarlevel(options, 1);
            optionstruct.maxinvarlevel(options, 2);
            optionstruct.tc_level(options, 10);

            int rowSize = new FixBS(sz).words().length;
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
//                FixBS fbs = new FixBS(sz);
//                for (int j = 0; j < sz; j++) {
//                    if (gw.edge(i, j)) {
//                        fbs.set(j);
//                    }
//                }
//                for (int j = 0; j < rowSize; j++) {
//                    g[sh + j] = Long.reverse(fbs.words()[j]);
//                }
//                sh = sh + rowSize;
            }
            int[] lab = new int[sz];
            int[] ptn = new int[sz];
            Map<Integer, List<Integer>> grouped = IntStream.range(0, sz).boxed()
                    .collect(Collectors.groupingBy(gw::color));
            int cnt = 0;
            int colorCount = gw.colors();
            for (int i = 0; i < colorCount; i++) {
                List<Integer> ints = grouped.get(i);
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
            int workArea = 5000 * 50;
            MemorySegment workspace = arena.allocate(ValueLayout.JAVA_LONG, workArea);
            nautinv_h.nauty(nativeG, nativeLab, nativePtn,
                    MemorySegment.NULL, nativeOrbits, options, stats,
                    workspace, workArea, rowSize, sz, MemorySegment.NULL);
            System.out.println("Done");
            int[] orbits = nativeOrbits.toArray(ValueLayout.JAVA_INT);
            System.out.println(Arrays.toString(orbits));
            System.out.println(statsblk.grpsize1(stats));
//            // Read fields
//            int x = optionstruct.getcanon(options);
//            int y = optionstruct.digraph(options);
        }
    }

    private static long rev(long l) {
        FixBS fbs = new FixBS(new long[]{l});
        FixBS copy = new FixBS(64);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                boolean bit = fbs.get(i * 8 + j);
                copy.set((8 - i - 1) * 8 + j, bit);
            }
        }
        return copy.words()[0];
    }
}
