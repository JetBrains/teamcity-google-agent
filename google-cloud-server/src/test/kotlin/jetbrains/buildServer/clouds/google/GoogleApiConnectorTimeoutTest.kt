package jetbrains.buildServer.clouds.google

import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.compute.v1.AggregatedListInstancesRequest
import com.google.cloud.compute.v1.InstancesClient
import com.google.cloud.compute.v1.InstancesSettings
import org.testng.Assert
import org.testng.annotations.AfterTest
import org.testng.annotations.Test
import java.io.IOException
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.TimeUnit

class GoogleApiConnectorTimeoutTest {

    private val heldSockets = mutableListOf<java.net.Socket>()
    private var serverSocket: ServerSocket? = null

    @AfterTest
    fun tearDown() {
        synchronized(heldSockets) { heldSockets.forEach { it.close() } }
        serverSocket?.close()
    }

    @Test(timeOut = 120_000)
    fun fetchInstancesDoesNotHangForever() {
        // Simulates TW-83415: an endpoint that accepts the connection but never responds.
        val heldSockets = mutableListOf<java.net.Socket>()
        val socket = ServerSocket(0).also { serverSocket = it }
        val acceptThread = Thread {
            while (!socket.isClosed) {
                try {
                    val accepted = socket.accept() // hold the connection, never respond
                    synchronized(heldSockets) { heldSockets.add(accepted) }
                } catch (ignored: IOException) {
                }
            }
        }
        acceptThread.isDaemon = true
        acceptThread.start()

        val timeoutSeconds = 3L
        val settings = InstancesSettings.newBuilder()
                .setEndpoint("http://127.0.0.1:${socket.localPort}")
                .setCredentialsProvider(FixedCredentialsProvider.create(GoogleCredentials.create(
                        com.google.auth.oauth2.AccessToken("fake-token", java.util.Date(Long.MAX_VALUE)))))
                .apply {
                    aggregatedListSettings().retrySettings = aggregatedListSettings().retrySettings
                            .toBuilder()
                            .setTotalTimeoutDuration(Duration.ofSeconds(timeoutSeconds))
                            .build()
                }
                .build()

        InstancesClient.create(settings).use { client ->
            val start = System.nanoTime()
            try {
                client.aggregatedList(AggregatedListInstancesRequest.newBuilder()
                                .setProject("test-project")
                                .build())
                        .iterateAll()
                        .forEach { }
                throw AssertionError("expected a failure against a silent endpoint")
            } catch (expected: Exception) {
                val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                Assert.assertTrue(
                        elapsedMs in (timeoutSeconds * 1000)..120_000,
                        "call should be bounded by the ${timeoutSeconds}s deadline, took ${elapsedMs}ms, failure was: $expected")
            }
        }
    }
}