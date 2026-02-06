# JNauty

## Motivation

I'm Java developer who likes mathematics. In my code there is often the need to calculate automorphism group of graph or combinatorial design. I wasn't able to find any usable and performant Java library for graph automorphisms. Nauty/traces nowadays is modern standard of this algorithms. I even tried to reimplement my own copy of nauty based on original [practical graph isomorphism](https://users.cecs.anu.edu.au/~bdm/papers/pgi.pdf) paper (fast enough, but still slower that the original). Fortunately, recently FFM API in Java was made stable and it allowed me to write wrapper for nauty library. It is developed for my own pusposes on Linux environment, but I assume that with slight modifications, it would be possible to have versions for Windows/Mac. So, PR and improvements are welcome. 

## Usage

Requirement: Java 25 or more.

`mvn clean install` from sources and add library as dependency to your maven/gradle/other project.

Usage is very straightforward. You need to implement `NautyGraph` interface with 2 obligatory and 1 default method. I didn't need oriented graphs, so graph should be non-oriented. Graph vertices are just numbers from 0 to size. It is possible to "color" vertices.

```
public interface NautyGraph {
    int vCount();

    default int vColor(int idx) {
        return 0;
    }

    boolean edge(int a, int b);
}
```

See tests for examples. Then just invoke
```GraphData data = JNauty.instance().nauty(gw)``` or ```GraphData data = JNauty.instance().traces(gw)```. You will get number of automorphisms, generators of automorphism group, orbits and canonical form of graph. These are two different algorithms, so test on your data which better suits you by performance.

(Optional) If you don't trust `.so` file bundled, then you need to build nauty 2.9.3 from [sources](https://pallini.di.uniroma1.it/) and replace `libnauty.so` in `resources` folder. Important! You should build thread-local version of nauty, or else you'll have issues in multithreaded environment. Command for building this version is:

```./configure --enable-tls; make clean; make```

## License

As original nauty, jnauty is distributed under [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## Acknowledgements and final notes

I would like to express my thanks to [Vedran Krčadinac](https://web.math.pmf.unizg.hr/~krcko/homepage.html) who explained me how nauty works in C and provided working "hello world" example and to Roman Obukhivskyi who helped me to fix issues related to C interaction and multithreaded environment.

If you like this library and would like to thank author - then help Ukraine which is fighting in war. You can donate [here](https://sokyra.space/en) or choose any other help for Ukraine foundation. 