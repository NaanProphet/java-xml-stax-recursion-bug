# java-xml-stax-recursion-bug
[![Build Status](https://travis-ci.org/NaanProphet/java-xml-stax-recursion-bug.svg?branch=master)](https://travis-ci.org/NaanProphet/java-xml-stax-recursion-bug)

## Versions Affected
* Mac OS X (locally)
  * **1.8.0_102** Java(TM) SE Runtime Environment (build 1.8.0_102-b14)
* Linux (via Travis)
  * **1.6.0_39** OpenJDK Runtime Environment (IcedTea6 1.13.11) (6b39-1.13.11-0ubuntu0.12.04.1)
  * **1.7.0_111** OpenJDK Runtime Environment (IcedTea 2.6.7) (7u111-2.6.7-0ubuntu0.12.04.2)
  * **1.7.0_80** Java(TM) SE Runtime Environment (build 1.7.0_80-b15)
  * **1.8.0_101** Java(TM) SE Runtime Environment (build 1.8.0_101-b13)

## Root Cause
When streaming large XML files using an event filter, the `EventFilterSupport#nextEvent` method throws a `StackOverflowError`. Solution changes recursion to while loops instead.

## Test Cases
The JUnit stress tests the API using a 100 MB XML file taken from the [Digital Bibliography Library Project](http://www.cs.washington.edu/research/xmldatasets/) using only 256 MB of max heap space. Note: tests must be run from Maven the `mvn test` goal (i.e. not from the IDE) in order for patched JVM arguments to take effect via the JUte maven plugin.

Note: for proper JUnit testing, this repo should be cloned via
```
git lfs clone
```
 rather than `git clone`. Otherwise, the XML test file will simply be a plaintext git lfs file resembling:
```
version https://git-lfs.github.com/spec/v1
oid sha256:2cffc115ba717045d3bf78aefaf4fdcd971878f420ff31716a1353df316277ef
size 133439113
```
which will then throw `java.util.zip.ZipException: Not in GZIP format`.

## Original Source Code
For reference, the original source code is available at http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/8u40-b25/com/sun/xml/internal/stream/EventFilterSupport.java/
