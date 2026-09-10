# Build Your Own Redis in Java

A lightweight Redis-like in-memory key-value database built from scratch using Java.

This project implements a custom TCP server, RESP protocol parsing, Redis-style commands, multiple data structures, TTL and expiration, persistence, concurrency handling, error handling, and performance benchmarking.

## Features

- TCP server running on port 6379
- RESP protocol parsing
- Redis-style command execution
- String operations
- Lists
- Sets
- Hashes
- Key expiration and TTL
- Atomic INCR
- SETNX
- Concurrent client handling
- Thread-safe data storage
- Snapshot-based persistence
- Backup snapshot and crash recovery
- Background expired-key cleanup
- Malformed request handling
- Command argument validation
- Wrong data-type handling
- Performance benchmarking

## Supported Commands

### Basic Commands

- PING
- SET
- GET
- DEL
- EXISTS
- KEYS
- MGET

### String Commands

- SETNX
- INCR

### TTL Commands

- EXPIRE
- TTL

### List Commands

- LPUSH
- RPUSH
- LPOP
- RPOP
- LRANGE

### Set Commands

- SADD
- SREM
- SISMEMBER
- SMEMBERS

### Hash Commands

- HSET
- HGET
- HDEL
- HGETALL

### Persistence

- SAVE

## Architecture

```text
Client
   |
   v
RedisServer
   |
   v
RESP Parser
   |
   v
CommandRegistry
   |
   v
RedisStore
   |
   +--> Data
   +--> TTL
   +--> Key Locks
   |
   v
SnapshotManager
   |
   v
Redis Snapshot

Component Responsibilities
RedisServer — Accepts TCP client connections and manages client threads.
RespParser — Parses incoming Redis Serialization Protocol (RESP) requests.
CommandRegistry — Validates commands, arguments, and executes Redis operations.
RedisStore — Stores data, expiration information, and manages per-key locking.
SnapshotManager — Handles persistence and snapshot recovery.
RespWriter — Sends properly formatted RESP responses to clients.
Technologies Used
Java
TCP/IP Socket Programming
RESP Protocol
ConcurrentHashMap
Java Concurrency
File I/O
PowerShell
Git
GitHub
Running the Project
1. Compile the Project

Open PowerShell in the src directory:

cd C:\Users\Anjana\OneDrive\Desktop\redis-java\src
javac *.java
2. Start the Server
java Main

The server starts on port 6379.

You should see:

Redis server started on port 6379...
Waiting for a client...
3. Send Commands

The server accepts Redis-style commands through a TCP connection using the RESP protocol.

Example commands:

SET name Anju
GET name

The project can also be tested using the PowerShell Redis-Command helper used during development.

Concurrency

The server supports multiple clients simultaneously using separate client threads.

Atomic operations such as INCR and SETNX are protected using synchronization and per-key locking.

Concurrent testing was performed with multiple clients updating the same keys. The final values matched the expected results, demonstrating correct handling of concurrent operations.

Persistence and Crash Recovery

The project supports snapshot-based persistence.

The server:

Loads the existing snapshot during startup.
Saves the current data using the SAVE command.
Creates a backup snapshot.
Uses the backup snapshot when the primary snapshot is corrupted or unavailable.

Crash-recovery testing was performed by saving data, terminating the server, corrupting the primary snapshot, and restarting the server. The backup snapshot was successfully used to recover previously saved data.

TTL and Expiration

Keys can be given an expiration time using:

EXPIRE key seconds

Remaining lifetime can be checked using:

TTL key

Expired keys are removed through normal key access and background cleanup.

Example:

SET name Anju
EXPIRE name 10
TTL name

After the expiration time, the key is no longer available.

Error Handling

The server handles various invalid and failure conditions, including:

Invalid RESP requests
Invalid array lengths
Invalid bulk-string lengths
Incomplete requests
Missing command arguments
Too many command arguments
Invalid integer values
Wrong data types
Missing keys
Connection failures
Server restart scenarios

Example:

GET

returns:

-ERR wrong number of arguments

Trying to use GET on a list produces a WRONGTYPE error.

Performance

Performance benchmarking was performed using 1,000 operations under different connection models.

New TCP Connection Per Operation
Operation	Time	Throughput
SET	1419.65 ms	704.40 ops/sec
GET	1377.43 ms	725.99 ops/sec
INCR	1747.05 ms	572.39 ops/sec
Concurrent INCR	770.10 ms	1298.54 ops/sec
Persistent TCP Connection
Operation	Time	Throughput
SET	253.72 ms	3941.35 ops/sec
GET	154.40 ms	6476.65 ops/sec
INCR	136.11 ms	7346.79 ops/sec
Concurrent Persistent Connection
Operation	Time	Throughput
SET	175.85 ms	5686.53 ops/sec
GET	166.25 ms	6015.04 ops/sec
INCR	136.98 ms	7300.17 ops/sec
Concurrent INCR	80.31 ms	12452.23 ops/sec

The results show that persistent TCP connections significantly improve throughput because repeated TCP connection setup and teardown overhead is avoided.

The concurrent persistent-connection benchmark achieved approximately 12.45K operations/second for the tested concurrent INCR workload while maintaining the expected final value.

These are local development benchmark results. Performance can vary depending on hardware, JVM version, operating-system load, and test conditions.

Testing

The project was tested for:

Basic Redis commands
Strings
Lists
Sets
Hashes
TTL and expiration
Persistence
Crash recovery
Concurrent operations
Atomic operations
Malformed RESP requests
Invalid command arguments
Wrong data types
Server restart
Connection failures
Performance
Project Structure
redis-java/
|
+-- src/
|   +-- Main.java
|   +-- RedisServer.java
|   +-- RedisStore.java
|   +-- RedisValue.java
|   +-- RespParser.java
|   +-- RespWriter.java
|   +-- CommandRegistry.java
|   +-- CommandHandler.java
|   +-- SnapshotManager.java
|   +-- RedisBenchmark.java
|   +-- RedisStoreTest.java
|   +-- threadtest.java
|
+-- README.md
+-- .gitignore
Learning Outcomes

Through this project, I gained practical experience with:

Network programming
TCP socket programming
Protocol parsing
Client-server architecture
Concurrent programming
Thread safety
Per-key synchronization
Data structures
TTL and expiration
Persistence
Crash recovery
Error handling
Performance benchmarking
Git and GitHub

Author
Anjana Pujari
## Live Deployment

The Redis server is deployed on Railway and exposed through a TCP Proxy.

- **Platform:** Railway
- **Protocol:** TCP
- **Internal Port:** 6379
- **Public Endpoint:** Sakura.proxy.rlwy.net:44516

The deployed server was tested remotely using:
- PING → +PONG
- SET name Anju → +OK
- GET name → Anju

> This is a raw TCP Redis-compatible server, so the endpoint is not an HTTP/HTTPS webpage.

