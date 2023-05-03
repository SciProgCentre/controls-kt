job("Build and run tests") {
    gradlew("amazoncorretto:11-alpine", "build")
}