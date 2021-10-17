# Xeres

This is an attempted reimplementation of [Retroshare](https://retroshare.cc) in Java.

## Supported platforms

- Windows (x86_64)
- Linux (x86_64)
- MacOS (x86_64) _untested_

## Build requirements

- Java 17

## Features

- [AES-NI](https://en.wikipedia.org/wiki/AES_instruction_set) support
- [JavaFX](https://openjfx.io/) UI
- Web UI
- High concurrency

## Download

https://xeres.io

## Donations

| Method | Address |
|---     |---      |
| GitHub | https://github.com/sponsors/zapek |
| PayPal | https://www.paypal.me/zapek666 |
| BTC    | bc1qn57zvp8s3h6renf805fan53kt7q4j963g7prvt |
| XMR    | 84czz4Vg44GaGmQF8Lst3uWEMyXLSahBffhrDd77jdVJEoKCtUJF96mGQ4XzcYrLG1JGaj2hr2sMoDoihQ52MT1jMBnucyu |
| GOST   | GM72AdtcAKLT8DGHSgDGeTA8Zsub23wL4K |
| ETH    | 0x7d9EfEe706c81227c73DA7814319301C6Bd63D05 |
| ZEN    | znePxvhiUQLp7arEEVvuC1rkofgJf3LZ2uw |
| DASH   | Xqa3APRoHKzrF1w8fWVHG3Ae5ZaaSZMgio |
| BAT    | https://github.com/zapek/Xeres/commits?author=zapek (use tip button in Brave browser)

## How to run

##### IntelliJ IDEA Ultimate

It is recommended to run the _XeresApplication_ Spring Boot configuration which is the most convenient and fastest way.  
Just make sure to configure it in the following way:

Select _Edit Configurations..._ of the _XeresApplication_ Spring Boot configuration.

Put the following _VM options_:

    -ea -Djava.net.preferIPv4Stack=true

And the following _Active profiles_:

    dev

Optionally, for faster build/test turnarounds you can add in the _program arguments_:

	--fast-shutdown

Then just run the _XeresApplication_ Spring Boot configuration.

##### IntelliJ Community Edition

Run the Gradle ``bootRun`` target. It's in the top right _Gradle_ panel, Tasks / application. It's already preconfigured.

(This way also works with IntelliJ IDEA Ultimate, but you'll miss some extras like colored debug output and faster launch)

##### Command line

###### Windows

	gradlew.bat

###### Linux

	./gradlew

To pass Xeres arguments, just use the args feature, for example:

	./gradlew bootRun --args="--no-gui --fast-shutdown"

(Use ``--help`` to know all arguments)

## How to set up the WebUI

_Note: the webui is currently nonfunctional._

Run the gradle tasks ``installAngular`` (if you don't already have Angular installed) then ``buildAngular``. The later will create the needed files that will be served by Xeres on ``localhost:1066``.

## Database debugging

With IntelliJ Ultimate, create the following Database connection with the built-in Datagrip client (aka the _Database_ tool window)

- Connection type: Embedded
- Driver: H2
- Path: select ``./data/userdata.mv.db``. If the file is not there, run Xeres once.
- Authentication: User & Password
- User: ``sa``
- There's no password

## Misc

The project was started on 2019-10-30.

##### Git branching model

The current plan is to use *master* for everything. Use a feature branch to work on a feature (for example, feature/165 if there's a ticket). Once it's ready, have someone review it then merge to master.

Releases will use tags and release branches if further fixes are needed.

https://reallifeprogramming.com/git-process-that-works-say-no-to-gitflow-50bf2038ccf7

## Useful Gradle tasks

##### Cleaning the build directory

run the ``clean`` task

##### Cleaning the Angular generated directory

run the ``cleanAngular`` task

##### Upgrading Gradle

- change the version in _build.gradle_ in the _wrapper_ section
- run the ``wrapper`` task
