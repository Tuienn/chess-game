package com.example.chess.nearby

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.example.chess.model.Move
import com.example.chess.model.GameState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quản lý Nearby Connections API cho P2P chess game
 */
class NearbyConnectionsManager(private val context: Context) {
    
    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val gson = Gson()
    
    // Service ID unique cho chess app
    private val SERVICE_ID = "com.example.chess.NEARBY_SERVICE"
    
    // Connection state
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // Discovered devices
    private val _discoveredDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<NearbyDevice>> = _discoveredDevices.asStateFlow()
    
    // Connected endpoint
    private var connectedEndpointId: String? = null
    
    // Callbacks
    private var onMoveReceived: ((Move) -> Unit)? = null
    private var onGameStateReceived: ((GameState) -> Unit)? = null
    private var onConnectionStatusChanged: ((Boolean, String?) -> Unit)? = null
    private var onTimerUpdate: ((Long, Long) -> Unit)? = null
    
    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Advertising : ConnectionState()
        object Discovering : ConnectionState()
        object Connecting : ConnectionState()
        data class Connected(val deviceName: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }
    
    data class NearbyDevice(
        val endpointId: String,
        val name: String,
        val serviceId: String
    )
    
    sealed class NearbyMessage {
        data class ChessMove(val move: Move) : NearbyMessage()
        data class GameSync(val gameState: GameState) : NearbyMessage()
        data class PlayerReady(val isReady: Boolean) : NearbyMessage()
        data class Disconnect(val reason: String) : NearbyMessage()
        data class TimerUpdate(val whiteTimeMs: Long, val blackTimeMs: Long) : NearbyMessage()
    }
    
    // Connection lifecycle callbacks
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d("NearbyChess", "Connection initiated with: ${connectionInfo.endpointName}")
            _connectionState.value = ConnectionState.Connecting
            
            // Auto accept all connections for now
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }
        
        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d("NearbyChess", "Connected successfully to: $endpointId")
                    connectedEndpointId = endpointId
                    _connectionState.value = ConnectionState.Connected("Connected Device")
                    onConnectionStatusChanged?.invoke(true, endpointId)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d("NearbyChess", "Connection rejected")
                    _connectionState.value = ConnectionState.Error("Connection rejected")
                    onConnectionStatusChanged?.invoke(false, "Connection rejected")
                }
                else -> {
                    Log.d("NearbyChess", "Connection failed: ${result.status}")
                    _connectionState.value = ConnectionState.Error("Connection failed")
                    onConnectionStatusChanged?.invoke(false, "Connection failed")
                }
            }
        }
        
        override fun onDisconnected(endpointId: String) {
            Log.d("NearbyChess", "Disconnected from: $endpointId")
            connectedEndpointId = null
            _connectionState.value = ConnectionState.Disconnected
            onConnectionStatusChanged?.invoke(false, "Disconnected")
        }
    }
    
    // Payload callback for receiving messages
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val message = String(payload.asBytes()!!)
            Log.d("NearbyChess", "Received message: $message")

            val nearbyMessage = parseNearbyMessage(message)
            if (nearbyMessage != null) {
                handleReceivedMessage(nearbyMessage)
            } else {
                Log.e("NearbyChess", "Failed to parse nearby message")
            }
        }
        
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Handle file transfer updates if needed
        }
    }
    
    // Endpoint discovery callback
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d("NearbyChess", "Endpoint found: ${info.endpointName}")
            val device = NearbyDevice(endpointId, info.endpointName, info.serviceId)
            val currentDevices = _discoveredDevices.value.toMutableList()
            if (!currentDevices.any { it.endpointId == endpointId }) {
                currentDevices.add(device)
                _discoveredDevices.value = currentDevices
            }
        }
        
        override fun onEndpointLost(endpointId: String) {
            Log.d("NearbyChess", "Endpoint lost: $endpointId")
            val currentDevices = _discoveredDevices.value.toMutableList()
            currentDevices.removeAll { it.endpointId == endpointId }
            _discoveredDevices.value = currentDevices
        }
    }
    
    /**
     * Start advertising to become discoverable
     */
    fun startAdvertising(localPlayerName: String): Flow<Boolean> = callbackFlow {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()
            
        connectionsClient.startAdvertising(
            localPlayerName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.d("NearbyChess", "Started advertising successfully")
            _connectionState.value = ConnectionState.Advertising
            trySend(true)
        }.addOnFailureListener { exception ->
            Log.e("NearbyChess", "Failed to start advertising", exception)
            _connectionState.value = ConnectionState.Error("Failed to start advertising: ${exception.message}")
            trySend(false)
        }
        
        awaitClose {
            stopAdvertising()
        }
    }
    
    /**
     * Start discovering nearby devices
     */
    fun startDiscovery(): Flow<Boolean> = callbackFlow {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()
            
        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.d("NearbyChess", "Started discovery successfully")
            _connectionState.value = ConnectionState.Discovering
            trySend(true)
        }.addOnFailureListener { exception ->
            Log.e("NearbyChess", "Failed to start discovery", exception)
            _connectionState.value = ConnectionState.Error("Failed to start discovery: ${exception.message}")
            trySend(false)
        }
        
        awaitClose {
            stopDiscovery()
        }
    }
    
    /**
     * Connect to a discovered device
     */
    fun connectToDevice(device: NearbyDevice, localPlayerName: String) {
        Log.d("NearbyChess", "Attempting to connect to: ${device.name}")
        _connectionState.value = ConnectionState.Connecting
        
        connectionsClient.requestConnection(
            localPlayerName,
            device.endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d("NearbyChess", "Connection request sent to: ${device.name}")
        }.addOnFailureListener { exception ->
            Log.e("NearbyChess", "Failed to request connection", exception)
            _connectionState.value = ConnectionState.Error("Failed to connect: ${exception.message}")
        }
    }
    
    /**
     * Send chess move to connected device
     */
    fun sendMove(move: Move) {
        connectedEndpointId?.let { endpointId ->
            val message = NearbyMessage.ChessMove(move)
            sendMessage(message, endpointId)
        }
    }
    
    /**
     * Send game state sync to connected device
     */
    fun sendGameState(gameState: GameState) {
        connectedEndpointId?.let { endpointId ->
            val message = NearbyMessage.GameSync(gameState)
            sendMessage(message, endpointId)
        }
    }
    
    /**
     * Send player ready status
     */
    fun sendPlayerReady(isReady: Boolean) {
        connectedEndpointId?.let { endpointId ->
            val message = NearbyMessage.PlayerReady(isReady)
            sendMessage(message, endpointId)
        }
    }
    
    private fun sendMessage(message: NearbyMessage, endpointId: String) {
        try {
            val jsonString = encodeNearbyMessage(message)
            val payload = Payload.fromBytes(jsonString.toByteArray())
            
            connectionsClient.sendPayload(endpointId, payload)
                .addOnSuccessListener {
                    Log.d("NearbyChess", "Message sent successfully")
                }
                .addOnFailureListener { exception ->
                    Log.e("NearbyChess", "Failed to send message", exception)
                }
        } catch (e: Exception) {
            Log.e("NearbyChess", "Error creating message payload", e)
        }
    }
    
    private fun handleReceivedMessage(message: NearbyMessage) {
        when (message) {
            is NearbyMessage.ChessMove -> {
                Log.d("NearbyChess", "Received chess move")
                onMoveReceived?.invoke(message.move)
            }
            is NearbyMessage.GameSync -> {
                Log.d("NearbyChess", "Received game state sync")
                onGameStateReceived?.invoke(message.gameState)
            }
            is NearbyMessage.PlayerReady -> {
                Log.d("NearbyChess", "Received player ready: ${message.isReady}")
                // Handle player ready state
            }
            is NearbyMessage.Disconnect -> {
                Log.d("NearbyChess", "Received disconnect: ${message.reason}")
                disconnect()
            }
            is NearbyMessage.TimerUpdate -> {
                Log.d("NearbyChess", "Received timer update: white=${message.whiteTimeMs}, black=${message.blackTimeMs}")
                onTimerUpdate?.invoke(message.whiteTimeMs, message.blackTimeMs)
            }
        }
    }

    private fun encodeNearbyMessage(message: NearbyMessage): String {
        val json = JsonObject()
        when (message) {
            is NearbyMessage.ChessMove -> {
                json.addProperty("type", "ChessMove")
                json.add("move", gson.toJsonTree(message.move))
            }
            is NearbyMessage.GameSync -> {
                json.addProperty("type", "GameSync")
                json.add("gameState", gson.toJsonTree(message.gameState))
            }
            is NearbyMessage.PlayerReady -> {
                json.addProperty("type", "PlayerReady")
                json.addProperty("isReady", message.isReady)
            }
            is NearbyMessage.Disconnect -> {
                json.addProperty("type", "Disconnect")
                json.addProperty("reason", message.reason)
            }
            is NearbyMessage.TimerUpdate -> {
                json.addProperty("type", "TimerUpdate")
                json.addProperty("whiteTimeMs", message.whiteTimeMs)
                json.addProperty("blackTimeMs", message.blackTimeMs)
            }
        }
        return gson.toJson(json)
    }

    private fun parseNearbyMessage(raw: String): NearbyMessage? {
        return try {
            val jsonElement = JsonParser.parseString(raw)
            if (!jsonElement.isJsonObject) {
                Log.e("NearbyChess", "Message is not a JSON object")
                return null
            }
            val jsonObject = jsonElement.asJsonObject
            val type = jsonObject.get("type")?.asString
            when (type) {
                "ChessMove" -> {
                    val move = gson.fromJson(jsonObject.get("move"), Move::class.java)
                    NearbyMessage.ChessMove(move)
                }
                "GameSync" -> {
                    val gameState = gson.fromJson(jsonObject.get("gameState"), GameState::class.java)
                    NearbyMessage.GameSync(gameState)
                }
                "PlayerReady" -> {
                    val isReady = jsonObject.get("isReady")?.asBoolean ?: false
                    NearbyMessage.PlayerReady(isReady)
                }
                "Disconnect" -> {
                    val reason = jsonObject.get("reason")?.asString ?: "Unknown"
                    NearbyMessage.Disconnect(reason)
                }
                "TimerUpdate" -> {
                    val whiteTimeMs = jsonObject.get("whiteTimeMs")?.asLong ?: 0
                    val blackTimeMs = jsonObject.get("blackTimeMs")?.asLong ?: 0
                    NearbyMessage.TimerUpdate(whiteTimeMs, blackTimeMs)
                }
                else -> {
                    Log.e("NearbyChess", "Unknown message type: $type")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("NearbyChess", "Error parsing nearby message", e)
            null
        }
    }
    
    /**
     * Set callback for when a move is received
     */
    fun setOnMoveReceivedCallback(callback: ((Move) -> Unit)?) {
        onMoveReceived = callback
    }
    
    /**
     * Set callback for when game state is received
     */
    fun setOnGameStateReceivedCallback(callback: ((GameState) -> Unit)?) {
        onGameStateReceived = callback
    }
    
    /**
     * Set callback for connection status changes
     */
    fun setOnConnectionStatusChangedCallback(callback: ((Boolean, String?) -> Unit)?) {
        onConnectionStatusChanged = callback
    }
    
    /**
     * Set callback for when timer update is received
     */
    fun setOnTimerUpdateCallback(callback: ((Long, Long) -> Unit)?) {
        onTimerUpdate = callback
    }
    
    /**
     * Send timer update to opponent
     */
    fun sendTimerUpdate(whiteTimeMs: Long, blackTimeMs: Long) {
        connectedEndpointId?.let { endpointId ->
            val message = NearbyMessage.TimerUpdate(whiteTimeMs, blackTimeMs)
            sendMessage(message, endpointId)
        }
    }

    /**
     * Stop advertising
     */
    fun stopAdvertising() {
        connectionsClient.stopAdvertising()
        if (_connectionState.value == ConnectionState.Advertising) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }
    
    /**
     * Stop discovery
     */
    fun stopDiscovery() {
        connectionsClient.stopDiscovery()
        _discoveredDevices.value = emptyList()
        if (_connectionState.value == ConnectionState.Discovering) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }
    
    /**
     * Disconnect from current connection
     */
    fun disconnect() {
        connectedEndpointId?.let { endpointId ->
            connectionsClient.disconnectFromEndpoint(endpointId)
        }
        connectedEndpointId = null
        _connectionState.value = ConnectionState.Disconnected
    }
    
    /**
     * Stop all connections and clean up
     */
    fun cleanup() {
        disconnect()
        stopAdvertising()
        stopDiscovery()
        connectionsClient.stopAllEndpoints()
    }
}