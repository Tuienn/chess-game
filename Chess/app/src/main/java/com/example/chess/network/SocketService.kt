package com.example.chess.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URISyntaxException
import com.example.chess.model.GameState
import com.example.chess.model.Move

data class AiMoveResult(
    val code: String,
    val yourMove: Move?,
    val aiMove: Move?,
    val sideToMove: String?,
    val gameOverResult: String?
)

class SocketService {
    private var socket: Socket? = null
    private val serverUrl = SOCKET_URL

    // Callbacks for game events
    private var onGameStateUpdateCallback: ((GameState) -> Unit)? = null
    private var onMoveReceivedCallback: ((Move) -> Unit)? = null
    private var onRoomJoinedCallback: ((String, String) -> Unit)? = null // (roomCode, playerColor)
    private var onOpponentJoinedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onAiMoveCallback: ((AiMoveResult) -> Unit)? = null
    private var onTimerUpdateCallback: ((Long?, Long?) -> Unit)? = null // (whiteTimeMs, blackTimeMs)
    
    companion object {
        private const val TAG = "SocketService"
        
        @Volatile
        private var INSTANCE: SocketService? = null
        
        fun getInstance(): SocketService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SocketService().also { INSTANCE = it }
            }
        }
    }
    
    fun connect() {
        try {
            val options = IO.Options()
            options.forceNew = true
            options.reconnection = true
            options.timeout = 5000
            
            socket = IO.socket(serverUrl, options)
            
            socket?.on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "Socket connected successfully")
            }?.on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "Socket disconnected")
            }?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "Socket connection error: ${args[0]}")
            }?.on("room_state") { args ->
                Log.d(TAG, "Room state received: ${args[0]}")
                handleRoomState(args[0] as JSONObject)
            }?.on("move_applied") { args ->
                Log.d(TAG, "Move applied: ${args[0]}")
                handleMoveApplied(args[0] as JSONObject)
            }?.on("room_joined") { args ->
                Log.d(TAG, "Room joined: ${args[0]}")
                handleRoomJoined(args[0] as JSONObject)
            }?.on("opponent_joined") { args ->
                Log.d(TAG, "Opponent joined")
                onOpponentJoinedCallback?.invoke()
            }?.on("ai_move") { args ->
                Log.d(TAG, "AI move received: ${args.getOrNull(0)}")
                (args.getOrNull(0) as? JSONObject)?.let { handleAiMove(it) }
            }?.on("error_msg") { args ->
                Log.e(TAG, "Server error: ${args[0]}")
                val errorMsg = (args[0] as JSONObject).optString("message", "Unknown error")
                onErrorCallback?.invoke(errorMsg)
            }
            socket?.connect()
            Log.d(TAG, "Attempting to connect to: $serverUrl")
            
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Socket connection failed", e)
        }
    }
    
    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        Log.d(TAG, "Socket disconnected and listeners removed")
    }
    
    fun isConnected(): Boolean {
        return socket?.connected() ?: false
    }
    
    
    // Join a room
    fun joinRoom(roomCode: String, uid: String) {
        if (isConnected()) {
            val data = JSONObject()
            data.put("code", roomCode)
            data.put("uid", uid)
            
            socket?.emit("join_room", data)
            Log.d(TAG, "Join room request sent: $roomCode with uid: $uid")
        } else {
            Log.w(TAG, "Socket not connected. Cannot join room.")
        }
    }
    
    // Send a move
    fun sendMove(roomCode: String, from: Int, to: Int, promo: String? = null) {
        Log.d(TAG, "sendMove called: roomCode=$roomCode, from=$from, to=$to, promo=$promo")
        Log.d(TAG, "Socket connected: ${isConnected()}")
        
        if (isConnected()) {
            val moveData = JSONObject()
            moveData.put("from", from)
            moveData.put("to", to)
            promo?.let { moveData.put("promo", it) }
            
            val data = JSONObject()
            data.put("code", roomCode)
            data.put("move", moveData)
            
            Log.d(TAG, "Emitting move event with data: $data")
            socket?.emit("move", data)
            Log.d(TAG, "Move sent: from $from to $to in room $roomCode")
        } else {
            Log.w(TAG, "Socket not connected. Cannot send move.")
        }
    }
    
    // Add custom event listeners
    fun addCustomListener(event: String, listener: Emitter.Listener) {
        socket?.on(event, listener)
    }
    
    fun removeCustomListener(event: String, listener: Emitter.Listener) {
        socket?.off(event, listener)
    }
    
    // Callback setters
    fun setOnGameStateUpdateCallback(callback: (GameState) -> Unit) {
        onGameStateUpdateCallback = callback
    }
    
    fun setOnMoveReceivedCallback(callback: ((Move) -> Unit)?) {
        onMoveReceivedCallback = callback
    }
    
    fun setOnRoomJoinedCallback(callback: ((String, String) -> Unit)?) {
        onRoomJoinedCallback = callback
    }
    
    fun setOnOpponentJoinedCallback(callback: (() -> Unit)?) {
        onOpponentJoinedCallback = callback
    }
    
    fun setOnErrorCallback(callback: ((String) -> Unit)?) {
        onErrorCallback = callback
    }

    fun setOnAiMoveCallback(callback: ((AiMoveResult) -> Unit)?) {
        onAiMoveCallback = callback
    }
    
    fun setOnTimerUpdateCallback(callback: ((Long?, Long?) -> Unit)?) {
        onTimerUpdateCallback = callback
    }
    
    // Private event handlers
    private fun handleRoomState(data: JSONObject) {
        try {
            // Parse room state and update game state if needed
            // For now, just log - server implementation will determine exact format
            Log.d(TAG, "Room state: $data")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling room state", e)
        }
    }
    
    private fun handleMoveApplied(data: JSONObject) {
        try {
            val move = parseMoveFromJson(data.optJSONObject("move"))
                ?: throw IllegalArgumentException("Move payload missing origin square")

            Log.d(
                TAG,
                "Received move: from ${move.from} to ${move.to}, promo: ${move.promo}, " +
                    "isCastle=${move.isCastle}, isEnPassant=${move.isEnPassant}, isDoublePawnPush=${move.isDoublePawnPush}"
            )
            onMoveReceivedCallback?.invoke(move)
            
            // Handle timer update if present
            if (data.has("whiteTimeMs") || data.has("blackTimeMs")) {
                val whiteTimeMs = if (data.has("whiteTimeMs") && !data.isNull("whiteTimeMs")) {
                    data.getLong("whiteTimeMs")
                } else null
                val blackTimeMs = if (data.has("blackTimeMs") && !data.isNull("blackTimeMs")) {
                    data.getLong("blackTimeMs")
                } else null
                onTimerUpdateCallback?.invoke(whiteTimeMs, blackTimeMs)
                Log.d(TAG, "Timer update: white=$whiteTimeMs, black=$blackTimeMs")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing move", e)
        }
    }
    
    private fun handleRoomJoined(data: JSONObject) {
        try {
            val roomCode = data.getString("code")
            val playerColor = data.getString("color") // "white" or "black"
            Log.d(TAG, "Joined room $roomCode as $playerColor")
            onRoomJoinedCallback?.invoke(roomCode, playerColor)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling room joined", e)
        }
    }

    private fun handleAiMove(data: JSONObject) {
        try {
            val code = data.optString("code")
            val yourMove = parseMoveFromJson(data.optJSONObject("yourMove"))
            val aiMove = parseMoveFromJson(data.optJSONObject("aiMove"))
            val sideToMove = data.optString("sideToMove", null)
            val gameOverResult = data.optJSONObject("gameOver")?.optString("result") ?: ""

            val result = AiMoveResult(
                code = code,
                yourMove = yourMove,
                aiMove = aiMove,
                sideToMove = sideToMove,
                gameOverResult = gameOverResult
            )
            Log.d(TAG, "Parsed ai_move: $result")
            onAiMoveCallback?.invoke(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling ai_move", e)
        }
    }

    private fun parseMoveFromJson(moveObj: JSONObject?): Move? {
        if (moveObj == null) return null
        val from = when {
            moveObj.has("from") && !moveObj.isNull("from") -> moveObj.getInt("from")
            moveObj.has("from_") && !moveObj.isNull("from_") -> moveObj.getInt("from_")
            else -> return null
        }
        val to = moveObj.getInt("to")
        val promo = if (moveObj.has("promo") && !moveObj.isNull("promo")) {
            moveObj.getString("promo").firstOrNull()
        } else {
            null
        }
        val isCastle = moveObj.optBoolean("isCastle", false)
        val isEnPassant = moveObj.optBoolean("isEnPassant", false)
        val isDoublePawnPush = moveObj.optBoolean("isDoublePawnPush", false)

        return Move(
            from = from,
            to = to,
            promo = promo,
            isCastle = isCastle,
            isEnPassant = isEnPassant,
            isDoublePawnPush = isDoublePawnPush
        )
    }

    fun sendMoveVsAi(
        roomCode: String,
        from: Int,
        to: Int,
        promo: String? = null,
        level: Int,
        thinkTimeMs: Int
    ) {
        if (!isConnected()) {
            Log.w(TAG, "Socket not connected. Cannot send move_vs_ai.")
            return
        }

        val moveData = JSONObject().apply {
            put("from", from)
            put("to", to)
            promo?.let { put("promo", it) }
        }

        val payload = JSONObject().apply {
            put("code", roomCode)
            put("move", moveData)
            put("level", level)
            put("thinkTimeMs", thinkTimeMs)
        }

        Log.d(TAG, "Emitting move_vs_ai with payload: $payload")
        socket?.emit("move_vs_ai", payload)
    }

    fun requestAiMove(roomCode: String, level: Int, thinkTimeMs: Int) {
        if (!isConnected()) {
            Log.w(TAG, "Socket not connected. Cannot request AI move.")
            return
        }

        val payload = JSONObject().apply {
            put("code", roomCode)
            put("level", level)
            put("thinkTimeMs", thinkTimeMs)
        }

        Log.d(TAG, "Requesting AI first move with payload: $payload")
        socket?.emit("move_vs_ai", payload)
    }
}
