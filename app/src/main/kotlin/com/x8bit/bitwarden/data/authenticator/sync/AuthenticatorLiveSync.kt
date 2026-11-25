package com.x8bit.bitwarden.data.authenticator.sync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.bitwarden.core.data.manager.dispatcher.DispatcherManager
import com.x8bit.bitwarden.data.auth.datasource.disk.AuthDiskSource
import com.x8bit.bitwarden.data.auth.repository.AuthRepository
import com.x8bit.bitwarden.data.authenticator.repository.AuthenticatorRepository
import com.x8bit.bitwarden.data.vault.manager.VaultLockManager
import com.x8bit.bitwarden.data.vault.repository.model.VaultUnlockData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Keeps the authenticator list current while the vault is unlocked.
 *
 * Vaultwarden tells every connected device about a create, edit, or delete on
 * `/notifications/hub`. This opens that socket and reloads the list. Coming back to the
 * app reloads as well. While the socket is down, the list is polled every few seconds.
 * There is no Google push dependency. Pull-to-refresh stays on the list screen.
 */
@Singleton
class AuthenticatorLiveSync @Inject constructor(
    private val repository: AuthenticatorRepository,
    private val authRepository: AuthRepository,
    private val authDiskSource: AuthDiskSource,
    private val vaultLockManager: VaultLockManager,
    dispatcherManager: DispatcherManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcherManager.io)
    private val hubReady = AtomicBoolean(false)
    private val sockets = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(PING_SECONDS, TimeUnit.SECONDS)
        .build()

    init {
        scope.launch(dispatcherManager.main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        reload()
                    }
                },
            )
        }
        scope.launch { watchHub() }
        scope.launch { pollWhileDisconnected() }
    }

    private suspend fun pollWhileDisconnected() {
        while (coroutineContext.isActive) {
            delay(POLL_MS)
            if (!hubReady.get()) {
                reload()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun watchHub() {
        authRepository.userStateFlow
            .flatMapLatest { state ->
                val userId = state?.activeUserId
                if (state == null || userId == null) {
                    flowOf(null)
                } else {
                    combine(
                        vaultLockManager.vaultUnlockDataStateFlow,
                        authDiskSource.getAccountTokensFlow(userId),
                    ) { unlocks, tokens ->
                        val unlocked = unlocks.any { unlock ->
                            unlock.userId == userId &&
                                unlock.status == VaultUnlockData.Status.UNLOCKED
                        }
                        val token = tokens?.accessToken
                        if (!unlocked || token.isNullOrBlank()) {
                            null
                        } else {
                            authenticatorHubUrl(
                                state.activeAccount.environment.environmentUrlData,
                                token,
                            )
                        }
                    }
                }
            }
            .distinctUntilChanged()
            .collectLatest { hubUrl ->
                if (hubUrl == null) {
                    hubReady.set(false)
                } else {
                    stayConnected(hubUrl)
                    hubReady.set(false)
                }
            }
    }

    private suspend fun stayConnected(hubUrl: String) {
        var pause = FIRST_RETRY_MS
        while (coroutineContext.isActive) {
            val openedAt = System.currentTimeMillis()
            listen(hubUrl)
            val lived = System.currentTimeMillis() - openedAt
            pause = if (lived > SETTLED_CONNECTION_MS) {
                FIRST_RETRY_MS
            } else {
                (pause * 2).coerceAtMost(MAX_RETRY_MS)
            }
            delay(pause)
        }
    }

    private suspend fun listen(hubUrl: String) {
        suspendCancellableCoroutine { continuation ->
            val socket = sockets.newWebSocket(
                Request.Builder().url(hubUrl).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        hubReady.set(true)
                        webSocket.send(HANDSHAKE)
                        reload()
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        val type = authenticatorNotificationType(bytes.toByteArray())
                        if (
                            type == AUTHENTICATOR_CREATED ||
                            type == AUTHENTICATOR_UPDATED ||
                            type == AUTHENTICATOR_DELETED
                        ) {
                            reload()
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(NORMAL_CLOSE, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        hubReady.set(false)
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        hubReady.set(false)
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                },
            )
            continuation.invokeOnCancellation { socket.cancel() }
        }
    }

    private fun reload() {
        scope.launch { repository.refresh() }
    }
}

private const val HANDSHAKE: String = "{\"protocol\":\"messagepack\",\"version\":1}\u001e"
private const val NORMAL_CLOSE: Int = 1000
private const val PING_SECONDS: Long = 15
private const val FIRST_RETRY_MS: Long = 1_000
private const val MAX_RETRY_MS: Long = 30_000
private const val SETTLED_CONNECTION_MS: Long = 10_000
private const val POLL_MS: Long = 3_000
