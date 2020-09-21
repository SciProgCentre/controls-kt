package hep.dataforge.control.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.utils.io.ByteReadChannel

suspend fun HttpClient.sse(address: String) = get<HttpStatement>(address).execute { response: HttpResponse ->
    // Response is not downloaded here.
    val channel = response.receive<ByteReadChannel>()
}