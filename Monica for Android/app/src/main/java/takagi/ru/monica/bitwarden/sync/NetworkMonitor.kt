package takagi.ru.monica.bitwarden.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 网络状态监控器
 * 
 * 用于监听网络连接状态变化，在网络恢复时触发同步队列处理。
 * 采用 Telegram 风格的离线优先同步策略。
 */
class NetworkMonitor(private val context: Context) {
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isOnline = MutableStateFlow(checkCurrentNetworkState())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    
    private val _isWifiConnected = MutableStateFlow(checkWifiState())
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    /**
     * 网络状态数据类
     */
    data class NetworkState(
        val isOnline: Boolean,
        val isWifi: Boolean,
        val isCellular: Boolean,
        val isMetered: Boolean
    ) {
        companion object {
            val Offline = NetworkState(
                isOnline = false,
                isWifi = false,
                isCellular = false,
                isMetered = true
            )
        }
    }
    
    /**
     * 获取网络状态变化的 Flow
     * 每当网络状态变化时会发射新值
     */
    val networkStateFlow: Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val state = getNetworkState(network)
                trySend(state)
                _isOnline.value = state.isOnline
                _isWifiConnected.value = state.isWifi
            }
            
            override fun onLost(network: Network) {
                // 检查是否还有其他可用网络
                val currentState = getCurrentNetworkState()
                trySend(currentState)
                _isOnline.value = currentState.isOnline
                _isWifiConnected.value = currentState.isWifi
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val state = parseNetworkCapabilities(networkCapabilities)
                trySend(state)
                _isOnline.value = state.isOnline
                _isWifiConnected.value = state.isWifi
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, callback)
        
        // 发送初始状态
        trySend(getCurrentNetworkState())
        
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
    
    /**
     * 开始监听网络状态
     */
    fun startMonitoring() {
        if (networkCallback != null) return
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
                _isWifiConnected.value = checkWifiState()
            }
            
            override fun onLost(network: Network) {
                _isOnline.value = checkCurrentNetworkState()
                _isWifiConnected.value = checkWifiState()
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                _isOnline.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                _isWifiConnected.value = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }
    
    /**
     * 停止监听网络状态
     */
    fun stopMonitoring() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
        }
    }
    
    /**
     * 检查当前网络状态
     */
    private fun checkCurrentNetworkState(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    /**
     * 检查当前是否连接 WiFi
     */
    private fun checkWifiState(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    /**
     * 获取指定网络的状态
     */
    private fun getNetworkState(network: Network): NetworkState {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkState.Offline
        return parseNetworkCapabilities(capabilities)
    }
    
    /**
     * 获取当前网络状态
     */
    fun getCurrentNetworkState(): NetworkState {
        val network = connectivityManager.activeNetwork ?: return NetworkState.Offline
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return NetworkState.Offline
        return parseNetworkCapabilities(capabilities)
    }
    
    /**
     * 解析网络能力为 NetworkState
     */
    private fun parseNetworkCapabilities(capabilities: NetworkCapabilities): NetworkState {
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        
        return NetworkState(
            isOnline = hasInternet,
            isWifi = isWifi,
            isCellular = isCellular,
            isMetered = isMetered
        )
    }
    
    /**
     * 检查是否可以同步（根据设置）
     * @param syncOnWifiOnly 是否仅在 WiFi 下同步
     */
    fun canSync(syncOnWifiOnly: Boolean): Boolean {
        if (!_isOnline.value) return false
        if (syncOnWifiOnly && !_isWifiConnected.value) return false
        return true
    }
}
