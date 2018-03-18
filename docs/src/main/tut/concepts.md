---
layout: docs
title:  "Concepts"
position: 1
---
* TOC
{:toc}

# Concepts 
Streams provide a convienent way of expressing [coinductive](https://en.wikipedia.org/wiki/Coinduction) problems, meaning the input may never end, e.g. a web server.

A Stream is essentially models an infinite collection of units of asynchronous work. Streams may be piped through other streams and sent to sinks.

## Units of Execution

### Stream

A stream can be represented in the abstract as `F[A]` where `F` is the type specific to the library (see below) and `A` is the value type contained within that stream. E.g `Observable[Int]` in Monix.

### Pipe

A pipe can be represented `F[A] => F[B]` where `F` is a stream. This allows multiple streams to be composed.

### Sink

A sink can be represented `F[A] => G[B]` where `F` is a stream and `G` is the materialized value (see below).

## Internal Types

|Library|Stream Type|Asyncronous Type|Materialized Type|
|-|-|-|-|
|Akka Streams|`Source`|`Future`|`RunnableGraph`|
|Monix Reactive|`Observable`|`Task`|`Task`|
|Monix Tail|`Iterant`|Any `Effect`*|Any `Effect`*|
|FS2|`Stream`|Any `Effect`*|Any `Effect`*|

*`Effect` refers to [Cats Effect](https://github.com/typelevel/cats-effect/), where `IO` is the reference implementation, but this could be anything, for example Monix's `Task`.

### Materialized Types

Notice that only Akka Streams has a different materialized type. This is because `Future` is eagerly evaluated, meaning that the moment it is assigned to a value it will start doing its work. Eager evaluation is at odds with reactive streaming concepts, where the you can construct a graph from many logical parts and as a separate activity execute that graph as a fully connected stream. The type `RunnableGraph` represents lazy evaluation of the stream, only when `.run()` is called does the execution begin.

Monix and FS2 are different in this respect as `Task` and `Effect` are lazily evaluated and therefore the materialized type can be the same as the asyncronous type used for each unit of work within the stream.

For example the result of sending a source to a sink which collects the result in a `Seq` in Akka Streams will be `RunnableGraph[Future[Seq[A]]]`, however because both the materialized and asychronous types in Monix and Fs2 are the same the effects can be captured in a single type, e.g. `Task[Seq[A]]` or `IO[Seq[A]]`.