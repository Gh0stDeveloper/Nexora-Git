package com.nexora.git.core.editor.lsp

import javax.inject.Inject
import org.json.JSONObject

data class LspJsonRpcResponse(
    val id: Long?,
    val result: Any?,
    val errorCode: Int?,
    val errorMessage: String?,
)

class LspJsonRpcCodec @Inject constructor() {

    fun request(
        id: Long,
        method: String,
        params: JSONObject = JSONObject(),
    ): String {
        require(id >= 0) { "JSON-RPC request id must be non-negative." }
        require(method.isNotBlank()) {
            "JSON-RPC method is required."
        }

        return JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)
            .toString()
    }

    fun notification(
        method: String,
        params: JSONObject = JSONObject(),
    ): String {
        require(method.isNotBlank()) {
            "JSON-RPC method is required."
        }

        return JSONObject()
            .put("jsonrpc", "2.0")
            .put("method", method)
            .put("params", params)
            .toString()
    }

    fun parseResponse(payload: String): LspJsonRpcResponse {
        val root = JSONObject(payload)
        require(root.optString("jsonrpc") == "2.0") {
            "Unsupported JSON-RPC version."
        }

        val error = root.optJSONObject("error")
        return LspJsonRpcResponse(
            id = if (root.has("id") && !root.isNull("id")) {
                root.getLong("id")
            } else {
                null
            },
            result = if (root.has("result") && !root.isNull("result")) {
                root.get("result")
            } else {
                null
            },
            errorCode = error?.optInt("code"),
            errorMessage = error?.optString("message")
                ?.takeIf(String::isNotBlank),
        )
    }
}
