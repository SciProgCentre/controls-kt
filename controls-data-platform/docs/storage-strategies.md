### Storing individual messages in a relational database

Each device is a source of `DeviceMessage` events. The flow of such events could be used to fully reconstruct device history (including lifecycle events and state changes). The size of such storage could be different for different databases.

We tested storing property changes in H2 database and it took 16.9 Mb of disk space for 100000 messages (169 bytes/message). Each message information payload is a single Double value (8 bytes). The informational overhead is caused by the following factors:
* Each message has a unique timestamp.
* Each message has additional fields like `sourceDevice` and `targetDevice`.
* Memory alignment.
* Database indexes.

Results for H2 database (100 000 events) write and read performance are the following:

```
Write time: 2.2s
Read time: 789ms
Storage size: 16.9 MB
```

The result for timescaleDB:

```
Storage size before test: 65 MB (68862244 bytes)
Write time: 28.499982200s
Read time: 7.113620901s
Storage size after test: 102 MB (107085632 bytes)
```

The result for timescaleDB with `&reWriteBatchedInserts=true`:

```
Storage size before test: 65 MB (68829476 bytes)
Write time: 5.228984600s
Read time: 1.002196600s
Storage size after test: 101 MB (106833208 bytes)
```

### Storing compressed messages in the file system

Another way to store device messages is to represent them as a time rows, where each row represents values read from a set of sensors in a single moment. The rows could be stored in a table. We use json object format for individual rows and then compress the list of rows using the Deflate algorithm. To reduce the size even further, we use value compression explained in [compression document](compression.md).

Zipped rows are stored in dataforge envelope file format that allows storing binary data and human-readable metadata in the same file. For test we used 50 columns of Double (8 bytes) valuues. Subsequent rows are formed with the random walk method with uniform (-0.1, 0.1) distribution.

The following scenarios were tested:
* No compression
* Skip repeating values
* Skip values within 0.1 margin

Scenario: No compression
File size: 1015.818359375 KB
Write time: 531.1928 ms
Read time: 171.4252 ms
Number of rows in result: 2000

Scenario: Skip repeating values
File size: 1015.818359375 KB
Write time: 292.0378 ms
Read time: 107.6675 ms
Number of rows in result: 2000

Scenario: Skip values within 0.1 margin
File size: 207.78125 KB
Write time: 67.1821 ms
Read time: 28.7131 ms
Number of rows in result: 2000

Write and read times are 10 times faster than relational database storage. The compressed size (without value filtration) in about 10 bytes per value, which is close to the lossless informational limit (8 bytes).