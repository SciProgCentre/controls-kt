# Module device-collective

# Running demo from gradle

1. Install JDK 17-21 in the system (for example, from https://sdkman.io/jdks or https://github.com/ScoopInstaller/Java).
2. Clone the repository with Git.
3. Run `./gradlew :demo:device-collective:run` from the project root directory.

# Install distribution

1. Install JDK 17-21 in the system (for example, from https://sdkman.io/jdks or https://github.com/ScoopInstaller/Java).
2. Clone the repository with Git.
3. Run `./gradlew :demo:device-collective:packageUberJarForCurrentOS` from the project root directory.
4. Go to `build/compose/jars/device-collective-<your OS>-<version>.jar`. You can copy it and run with `java -jar <full-name>.jar`
