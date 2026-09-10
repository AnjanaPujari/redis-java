# Build Your Own Redis in Java

A lightweight Redis-like in-memory key-value database built from scratch using Java.

This project implements a custom TCP server, RESP protocol parsing, Redis-style commands, data structures, TTL/expiration, persistence, concurrency handling, and performance benchmarking.

## Features

- TCP server on port 6379
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
- Persistence using snapshots
- Backup snapshot and crash recovery
- Background expired-key cleanup
- Error and malformed-request handling
- Performance benchmarking
- Thread-safe data storage

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

Technologies Used
Java
TCP/IP Socket Programming
RESP Protocol
ConcurrentHashMap
Java Concurrency
File I/O
PowerShell
Git & GitHub
Running the Project
1. Compile

Open a terminal in the src directory:

cd C:\Users\Anjana\OneDrive\Desktop\redis-java\src
javac *.java
2. Start the Server
java Main

The server starts on port 6379.

3. Send Commands

The server accepts Redis-style commands through a TCP connection using the RESP protocol.

Example:

SET name Anju
GET name
Concurrency

The server supports multiple clients simultaneously using separate client threads.

Atomic operations such as INCR and SETNX are protected using synchronization and per-key locking.

Concurrent testing confirmed that multiple clients can update the same key while maintaining correct final values.

Persistence

The project supports snapshot-based persistence.

The server:

Loads the existing snapshot during startup.
Saves data using the SAVE command.
Creates a backup snapshot.
Uses the backup during recovery if the primary snapshot is corrupted.
TTL and Expiration

Keys can be given an expiration time using:

EXPIRE key seconds

Remaining lifetime can be checked using:

TTL key

Expired keys are removed automatically through background cleanup as well as normal key access.

Error Handling

The server handles:

Invalid RESP requests
Invalid array lengths
Invalid bulk-string lengths
Missing arguments
Too many arguments
Invalid integer values
Wrong data types
Missing keys
Connection failures

Example:

GET

returns:

-ERR wrong number of arguments
Performance

Benchmarking was performed using both new TCP connections and persistent connections.

Persistent connections significantly improved throughput by removing repeated TCP connection setup and teardown overhead.

A concurrent persistent-connection benchmark achieved approximately:

12,452 operations/second

for the tested concurrent INCR workload on the local development machine.

Benchmark results depend on hardware, JVM version, operating-system load, and test conditions.

Testing

The project was tested for:

Basic commands
Data structures
TTL and expiration
Persistence
Crash recovery
Concurrent operations
Atomic operations
Malformed RESP requests
Invalid command arguments
Wrong data types
Server restart and connection failures
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
|   +-- Benchmark.java
|   +-- RedisStoreTest.java
|
+-- README.md
+-- redis.snapshot
Learning Outcomes

Through this project, I worked with:

Network programming
TCP sockets
Protocol parsing
Concurrent programming
Thread safety
Data structures
Persistence
Crash recovery
Error handling
Performance benchmarking
Git and GitHub

Author
Anjana Pujari