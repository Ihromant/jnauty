package ua.ihromant.jnauty.helper;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Liner {
    private final int pointCount;
    private final int[][] lines;
    private final FixBS[] flags;
    private final int[][] lookup;
    private final int[][] beams;
    private final int[][] intersections;

    private static final String FANO = """
            1110000
            1001100
            0101010
            1000011
            0011001
            0100101
            0010110
            """;

    public static Liner fano() {
        boolean[][] inc = new boolean[7][7];
        String[] lns = FANO.lines().toArray(String[]::new);
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                inc[i][j] = lns[j].charAt(i) == '1';
            }
        }
        return Liner.byIncidence(inc);
    }

    public Liner(int[][] lines) {
        this(Arrays.stream(lines).mapToInt(l -> Arrays.stream(l).max().orElseThrow()).max().orElseThrow() + 1, lines);
    }

    public Liner(int pointCount, int[][] lines) {
        this.pointCount = pointCount;
        this.lines = lines;
        this.flags = IntStream.range(0, lines.length).mapToObj(_ -> new FixBS(pointCount)).toArray(FixBS[]::new);
        int[] beamCounts = new int[pointCount];
        for (int i = 0; i < lines.length; i++) {
            int[] line = lines[i];
            for (int pt : line) {
                flags[i].set(pt);
                beamCounts[pt]++;
            }
        }
        this.beams = new int[pointCount][];
        for (int pt = 0; pt < pointCount; pt++) {
            int bc = beamCounts[pt];
            beams[pt] = new int[bc];
            int idx = 0;
            for (int ln = 0; ln < lines.length; ln++) {
                if (flags[ln].get(pt)) {
                    beams[pt][idx++] = ln;
                }
            }
        }
        this.lookup = generateLookup();
        this.intersections = generateIntersections();
    }

    public static Liner byIncidence(boolean[][] flags) {
        int pointCount = flags[0].length;
        int[][] lines = new int[flags.length][];
        for (int l = 0; l < flags.length; l++) {
            int ll = l;
            lines[l] = IntStream.range(0, pointCount).filter(p -> flags[ll][p]).toArray();
        }
        return new Liner(pointCount, lines);
    }

    public static Liner byDiffFamily(int[]... base) {
        int pointCount = Arrays.stream(base).mapToInt(arr -> arr.length * (arr.length - 1)).sum() + 1;
        int[][] lines = Stream.of(base).flatMap(arr -> IntStream.range(0, pointCount).mapToObj(idx -> {
            FixBS res = new FixBS(pointCount);
            for (int shift : arr) {
                res.set((idx + shift) % pointCount);
            }
            return res;
        })).map(FixBS::toArray).toArray(int[][]::new);
        return new Liner(pointCount, lines);
    }

    public static Liner byDiffFamily(int pointCount, int[]... base) {
        int[] lastBlock = base[base.length - 1];
        int k = lastBlock.length; // assuming that difference family is correct
        boolean slanted = pointCount % k == 0;
        int groupCard = slanted && lastBlock[k - 1] == pointCount - 1 ? pointCount - 1 : pointCount;
        boolean hasFixed = groupCard != pointCount;
        int[][] lines = Stream.concat(Arrays.stream(base, 0, slanted ? base.length - 1 : base.length)
                .flatMap(arr -> IntStream.range(0, groupCard).mapToObj(idx -> {
                    FixBS res = new FixBS(pointCount);
                    for (int shift : arr) {
                        res.set((idx + shift) % groupCard);
                    }
                    return res;
                })), slanted ? IntStream.range(0, hasFixed ? groupCard / (k - 1) : groupCard / k).mapToObj(idx -> {
            FixBS res = new FixBS(pointCount);
            for (int shift : lastBlock) {
                res.set(shift == groupCard ? shift : (idx + shift) % groupCard);
            }
            return res;
        }) : Stream.of()).map(bs -> bs.stream().toArray()).toArray(int[][]::new);
        return new Liner(pointCount, lines);
    }

    private int[][] generateLookup() {
        int[][] result = new int[pointCount][pointCount];
        for (int[] p : result) {
            Arrays.fill(p, -1);
        }
        for (int l = 0; l < lines.length; l++) {
            int[] line = lines[l];
            for (int i = 0; i < line.length; i++) {
                int p1 = line[i];
                for (int j = i + 1; j < line.length; j++) {
                    int p2 = line[j];
                    if (result[p1][p2] >= 0) {
                        throw new IllegalStateException();
                    }
                    result[p1][p2] = l;
                    result[p2][p1] = l;
                }
            }
        }
        return result;
    }

    private int[][] generateIntersections() {
        int[][] result = new int[lines.length][lines.length];
        for (int[] arr : result) {
            Arrays.fill(arr, -1);
        }
        for (int p = 0; p < pointCount; p++) {
            int[] beam = beams[p];
            for (int i = 0; i < beam.length; i++) {
                int l1 = beam[i];
                for (int j = i + 1; j < beam.length; j++) {
                    int l2 = beam[j];
                    result[l1][l2] = p;
                    result[l2][l1] = p;
                }
            }
        }
        return result;
    }

    public int pointCount() {
        return pointCount;
    }

    public int lineCount() {
        return lines.length;
    }

    public boolean flag(int line, int point) {
        return flags[line].get(point);
    }

    public int[] line(int line) {
        return lines[line];
    }

    public int line(int p1, int p2) {
        return lookup[p1][p2];
    }

    public int[] lines(int point) {
        return beams[point];
    }

    public int[] point(int point) {
        return beams[point];
    }

    public int intersection(int l1, int l2) {
        return intersections[l1][l2];
    }

    public int[] points(int line) {
        return lines[line];
    }

    public int[][] lines() {
        return lines;
    }

    public boolean collinear(int... points) {
        if (points.length == 0) {
            return true;
        }
        int first = points[0];
        for (int i = 1; i < points.length; i++) {
            int second = points[i];
            if (first != second) {
                FixBS fgs = flags[line(first, second)];
                return Arrays.stream(points, i + 1, points.length).allMatch(fgs::get);
            }
        }
        return true;
    }

    public String lineToString(int line) {
        return Arrays.toString(lines[line]);
    }

    public FixBS hull(int... points) {
        FixBS base = new FixBS(pointCount);
        for (int point : points) {
            base.set(point);
        }
        FixBS additional = base;
        while (!(additional = additional(base, additional)).isEmpty()) {
            base.or(additional);
        }
        return base;
    }

    public FixBS additional(FixBS base, FixBS add) {
        FixBS result = new FixBS(pointCount);
        for (int x = base.nextSetBit(0); x >= 0; x = base.nextSetBit(x + 1)) {
            for (int y = add.nextSetBit(0); y >= 0; y = add.nextSetBit(y + 1)) {
                int xy = line(x, y);
                if (xy < 0) {
                    continue;
                }
                result.or(flags[xy]);
            }
        }
        result.andNot(base);
        return result;
    }
}

