---
layout: docs
title:  "Usage"
position: 2
---
* TOC
{:toc}
# Usage

## Writer
The writer monad from Cats can be used in a streaming context to capture some state to be committed along with the main data at the end of the stream. This could be something like a Kafka offset or some stats regarding the execution.


[more to follow]

## Either
The either monad allows data to be routed via different pipes and to different sinks depending on whether it is in the left or right position.

[more to follow]