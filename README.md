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



# Participation

This project supports the Typelevel [code of conduct](http://typelevel.org/conduct.html) and aims that its channels
(mailing list, Gitter, github, etc.) to be welcoming environments for everyone.

# Licence

```
MIT License

Copyright (c) 2018 Chris Jansen

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```