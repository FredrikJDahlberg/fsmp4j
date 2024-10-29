Fast Efficient Fixed-Size Memory Pool For Java
==============================================

fsmp4j implements a fast and efficient fixed-size memory pool using the Foreign Function and Memory API as described in the paper [Fast Efficient Fixed-Size Memory Pool](https://arxiv.org/pdf/2210.16471).

Build
-----

### Java Build

Build the project with [Gradle](http://gradle.org/) using this [build.gradle](https://github.com/fredrikjdahlberg/fsmp4j/blob/main/build.gradle) file.

You require the following to build fsmp4j

* The Latest release of Java 23. fsmp4j is tested with Java 23.

Full clean and build:

    $ ./gradlew

Benchmarks
----------

### Jmh

Run benchmarks:

    $ ./gradlew jmh

License (See LICENSE file for full license)
-------------------------------------------

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.