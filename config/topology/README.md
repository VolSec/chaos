This folder should have several files in it:

- An IP mapping from CAIDA
- The latest inferred topology from CAIDA

You can get the latest topology from http://data.caida.org/datasets/as-relationships/serial-1/ and the latest prefix counts per AS, which can be used to build the IP mapping in the simulator, by processing a recent RIB file from http://archive.routeviews.org/.

The format of the first file should look like:

```
1|2|-1
1|3|-1
3|4|0
...
```

The format follows this guide:

```
ASN|ASN|RELATIONSHIP
```

RELATIONSHIP = -1 means the first ASN before the | is the provider for the second ASN. RELATIONSHIP = 0 means the two ASNs are peers.

The format of the second file should look like:

```
1 100
2 20
3 50
...
```

The format follows this guide:

```
ASN NUMBER_OF_PREFIXES_FOR_ASN
```

You can compute the number of prefixes per ASN by processing RIB files from something like RouteViews.
