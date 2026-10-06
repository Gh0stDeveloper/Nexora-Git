package com.nexora.git.core.git

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitLfsTransportTest {
    private val manager = GitLfsManager()
    private val credentials = object : GitCredentialProvider {
        override suspend fun credentialsFor(
            remoteUrl: String,
        ): GitTransportCredentials = GitTransportCredentials(
            username = "x-access-token",
            password = "test-token",
        )
    }

    @Test
    fun downloadsAndHydratesVerifiedPointerWithoutLeakingAuth() =
        runBlocking {
            val root = createRepository("nexora-lfs-download")
            try {
                val payload = "downloaded-lfs-object".toByteArray()
                val oid = sha256(payload)
                File(root, "asset.bin").writeText(
                    """
                    version https://git-lfs.github.com/spec/v1
                    oid sha256:$oid
                    size ${payload.size}
                    """.trimIndent() + "\n",
                )

                var actionHadAuthorization = false
                val client = OkHttpClient.Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            val request = chain.request()
                            when (request.url.host) {
                                "github.com" -> {
                                    assertTrue(
                                        request.header("Authorization")
                                            ?.startsWith("Basic ") == true,
                                    )
                                    response(
                                        request = request,
                                        body =
                                            """
                                            {
                                              "transfer":"basic",
                                              "objects":[{
                                                "oid":"$oid",
                                                "size":${payload.size},
                                                "actions":{
                                                  "download":{
                                                    "href":"https://objects.example.test/download"
                                                  }
                                                }
                                              }]
                                            }
                                            """.trimIndent()
                                                .toByteArray(),
                                        contentType =
                                            "application/vnd.git-lfs+json",
                                    )
                                }

                                "objects.example.test" -> {
                                    actionHadAuthorization =
                                        request.header("Authorization") != null
                                    response(
                                        request = request,
                                        body = payload,
                                        contentType =
                                            "application/octet-stream",
                                    )
                                }

                                else -> error("Unexpected host")
                            }
                        },
                    )
                    .build()

                val transport = GitLfsTransport(
                    client = client,
                    credentialProvider = credentials,
                    manager = manager,
                )

                val result = transport.downloadMissing(
                    repositoryPath = root.path,
                    remoteUrl =
                        "https://github.com/Gh0stDeveloper/Nexora-Git.git",
                )

                assertEquals(1, result.transferred)
                assertEquals(
                    "downloaded-lfs-object",
                    File(root, "asset.bin").readText(),
                )
                assertFalse(actionHadAuthorization)
            } finally {
                root.deleteRecursively()
            }
        }

    @Test
    fun uploadsLocalObjectsAndExecutesVerifyAction() =
        runBlocking {
            val root = createRepository("nexora-lfs-upload")
            try {
                val payload = "upload-lfs-object".toByteArray()
                val oid = sha256(payload)
                val objectFile = File(
                    root,
                    ".git/lfs/objects/" +
                        oid.substring(0, 2) + "/" +
                        oid.substring(2, 4) + "/" +
                        oid,
                )
                objectFile.parentFile?.mkdirs()
                objectFile.writeBytes(payload)

                var uploadSeen = false
                var verifySeen = false
                var actionHadAuthorization = false

                val client = OkHttpClient.Builder()
                    .addInterceptor(
                        Interceptor { chain ->
                            val request = chain.request()
                            when (request.url.host) {
                                "github.com" -> response(
                                    request = request,
                                    body =
                                        """
                                        {
                                          "transfer":"basic",
                                          "objects":[{
                                            "oid":"$oid",
                                            "size":${payload.size},
                                            "actions":{
                                              "upload":{
                                                "href":"https://objects.example.test/upload"
                                              },
                                              "verify":{
                                                "href":"https://objects.example.test/verify"
                                              }
                                            }
                                          }]
                                        }
                                        """.trimIndent().toByteArray(),
                                    contentType =
                                        "application/vnd.git-lfs+json",
                                )

                                "objects.example.test" -> {
                                    actionHadAuthorization =
                                        actionHadAuthorization ||
                                            request.header(
                                                "Authorization",
                                            ) != null
                                    if (request.url.encodedPath == "/upload") {
                                        uploadSeen =
                                            request.method == "PUT"
                                    } else if (
                                        request.url.encodedPath == "/verify"
                                    ) {
                                        verifySeen =
                                            request.method == "POST"
                                    }
                                    response(
                                        request = request,
                                        body = ByteArray(0),
                                        contentType = "text/plain",
                                    )
                                }

                                else -> error("Unexpected host")
                            }
                        },
                    )
                    .build()

                val transport = GitLfsTransport(
                    client = client,
                    credentialProvider = credentials,
                    manager = manager,
                )

                val result = transport.uploadPending(
                    repositoryPath = root.path,
                    remoteUrl =
                        "https://github.com/Gh0stDeveloper/Nexora-Git.git",
                )

                assertEquals(1, result.transferred)
                assertTrue(uploadSeen)
                assertTrue(verifySeen)
                assertFalse(actionHadAuthorization)
            } finally {
                root.deleteRecursively()
            }
        }

    private fun createRepository(prefix: String): File =
        java.nio.file.Files.createTempDirectory(prefix)
            .toFile()
            .also {
                File(it, ".git").mkdirs()
            }

    private fun response(
        request: okhttp3.Request,
        body: ByteArray,
        contentType: String,
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(
            body.toResponseBody(contentType.toMediaType()),
        )
        .build()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
