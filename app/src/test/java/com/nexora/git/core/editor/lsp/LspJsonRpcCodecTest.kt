package com.nexora.git.core.editor.lsp

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LspJsonRpcCodecTest {

    private val codec = LspJsonRpcCodec()

    @Test
    fun buildsProtocolCompliantRequest() {
        val payload = JSONObject(
            codec.request(
                id = 7,
                method = "textDocument/formatting",
                params = JSONObject().put("tabSize", 4),
            ),
        )

        assertEquals("2.0", payload.getString("jsonrpc"))
        assertEquals(7L, payload.getLong("id"))
        assertEquals(
            "textDocument/formatting",
            payload.getString("method"),
        )
        assertEquals(4, payload.getJSONObject("params").getInt("tabSize"))
    }

    @Test
    fun parsesSuccessAndErrorResponses() {
        val success = codec.parseResponse(
            """{"jsonrpc":"2.0","id":1,"result":{"ok":true}}""",
        )
        assertEquals(1L, success.id)
        assertEquals(
            true,
            (success.result as JSONObject).getBoolean("ok"),
        )
        assertNull(success.errorCode)

        val error = codec.parseResponse(
            """
            {
              "jsonrpc":"2.0",
              "id":2,
              "error":{"code":-32601,"message":"Method not found"}
            }
            """.trimIndent(),
        )
        assertEquals(-32601, error.errorCode)
        assertEquals("Method not found", error.errorMessage)
    }
}
