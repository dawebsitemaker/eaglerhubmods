# EaglerHubMods — Chrome .jar and /custom support

This repository adds a modern, easy-to-run injector and webserver that:

- Scans `chrome/` for .jar files and dynamically loads them into a plugin classloader (injector).
- Exposes `/custom/*` as a static resource mount backed by the `custom/` folder on disk and falling back to resources inside loaded JARs.
- Provides a simple HTTP web UI and instructions so you can host custom assets for the game and make "chrome" JAR mods available everywhere.

This is a minimal, well-documented starting point you can build on. It focuses on reliability, security, and clear behavior.

Requirements
- Java 11+ (tested with OpenJDK 11)
- Maven 3.6+

Quick start (from repository root)

1. Place mod jars into `chrome/` and static files into `custom/`.
2. Build and run the webserver (serves /custom and falls back to jar resources):

   mvn -pl webserver -am package
   java -jar webserver/target/webserver.jar

3. Run the injector which loads chrome/*.jar into a plugin classloader:

   mvn -pl injector -am package
   java -jar injector/target/injector.jar

How it works (high level)
- JarLoader scans the `chrome/` dir for *.jar, loads them as URLClassLoader(s) and returns their URLs. The injector sets a combined URLClassLoader as the thread context classloader so application code can find classes and resources.
- WebServer mounts `/custom/*`. On each request it first tries the `custom/` directory on disk. If not found, it inspects the loaded jar URLs and attempts to read the requested path as an entry inside the jar (without leading `/`).

Security notes
- By default jars are loaded without signature checks. Consider adding a manifest-based signing or a whitelist for production.
- The server only serves files under `custom/` and entries inside jars; it does not allow directory traversal (requests are normalized and validated).

Next steps / enhancements
- Add jar signature validation or manifest-based allowlist.
- Provide a plugin lifecycle manager to call known hook interfaces in mods.
- Add a more friendly web UI for managing mods and custom assets.
- Dockerfile and systemd unit templates.

License: MIT
