package com.example.chess.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URISyntaxException
import com.example.chess.model.GameState
import com.example.chess.model.Move

class SocketService {
    private var socket: Socket? = null
    private val serverUrl = "http://10.0.2.2:4001" // For Android emulator
    // Use "http://localhost:4001" for physical device on same network
    // or replace with your actual server IP
    
    // Callbacks for game events
    private var onGameStateUpdateCallback: ((GameState) -> Unit)? = null
    private var onMoveReceivedCallback: ((Move) -> Unit)? = null
    private var onRoomJoinedCallback: ((String, String) -> Unit)? = null // (roomCode, playerColor)
    private var onOpponentJoinedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    
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
    
    fun setOnMoveReceivedCallback(callback: (Move) -> Unit) {
        onMoveReceivedCallback = callback
    }
    
    fun setOnRoomJoinedCallback(callback: (String, String) -> Unit) {
        onRoomJoinedCallback = callback
    }
    
    fun setOnOpponentJoinedCallback(callback: () -> Unit) {
        onOpponentJoinedCallback = callback
    }
    
    fun setOnErrorCallback(callback: (String) -> Unit) {
        onErrorCallback = callback
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
            val moveObj = data.getJSONObject("move")
            val from = when {
                moveObj.has("from") && !moveObj.isNull("from") -> moveObj.getInt("from")
                moveObj.has("from_") && !moveObj.isNull("from_") -> moveObj.getInt("from_")
                else -> throw IllegalArgumentException("Move payload missing origin square")
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

            val move = Move(
                from = from,
                to = to,
                promo = promo,
                isCastle = isCastle,
                isEnPassant = isEnPassant,
                isDoublePawnPush = isDoublePawnPush
            )
            
            Log.d(
                TAG,
                "Received move: from $from to $to, promo: $promo, " +
                    "isCastle=$isCastle, isEnPassant=$isEnPassant, isDoublePawnPush=$isDoublePawnPush"
            )
            onMoveReceivedCallback?.invoke(move)
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
}
