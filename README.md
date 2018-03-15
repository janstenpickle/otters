# Otters - [Cats](https://typelevel.org/cats/) in Streams
[![Build Status](https://travis-ci.org/janstenpickle/otters.svg?branch=master)](https://travis-ci.org/janstenpickle/otters) [![codecov](https://codecov.io/gh/janstenpickle/otters/branch/master/graph/badge.svg)](https://codecov.io/gh/janstenpickle/otters)


Otters uses the [Cats](https://typelevel.org/cats/) library to provide a few typeclasess and useful syntax for working with streaming libraries.

Out of the box Otters supports
- [Akka Streams](https://doc.akka.io/docs/akka/2.5/stream/index.html?language=scala)
- [Monix Observable](https://monix.io/)
- [Monix Iterant](https://monix.io/)
- [FS2](https://functional-streams-for-scala.github.io/fs2/)

# Background & Motivation
All streaming libraries share common concepts, Otters attempts to create typeclasses and corresponding laws based on [Typelevel Cats](https://typelevel.org/cats/) so that common patterns may be shared between all implementations, without masking their differences.

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

# Usage

## Writer
The writer monad from Cats can be used in a streaming context to capture some state to be committed along with the main data at the end of the stream. This could be something like a Kafka offset or some stats regarding the execution.


[more to follow]

## Either
The either monad allows data to be routed via different pipes and to different sinks depending on whether it is in the left or right position.

[more to follow]

# Participation

This project supports the Typelevel [code of conduct](http://typelevel.org/conduct.html) and aims that its channels
(mailing list, Gitter, github, etc.) to be welcoming environments for everyone.