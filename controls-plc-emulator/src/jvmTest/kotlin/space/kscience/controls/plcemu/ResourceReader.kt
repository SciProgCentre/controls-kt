package space.kscience.controls.plcemu

public actual fun readResource(path: String): String = 
    IlEmulatorTest::class.java.getResourceAsStream(path)?.bufferedReader()?.readText() 
        ?: error("Resource not found: $path")
