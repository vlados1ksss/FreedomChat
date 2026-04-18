package com.vladdev.shared.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed class NetworkState {
    object Checking : NetworkState()

    object NoInternet : NetworkState()
    object ServerDown : NetworkState()
    object WhiteListMode : NetworkState()
}
class NetworkDiagnostics {

    suspend fun check(): NetworkState {
        return try {
            // 1. Проверка твоего сервера
            if (request("http://176.124.199.31/api")) {
                return NetworkState.Checking // или Online, если добавишь
            }

            // 2. Глобальный интернет
            val google = request("https://google.com")
            val cloudflare = request("https://cloudflare.com")

            if (google || cloudflare) {
                return NetworkState.ServerDown
            }

            // 3. RU сайты
            val yandex = request("https://yandex.ru")
            val ozon = request("https://ozon.ru")

            if (yandex || ozon) {
                return NetworkState.WhiteListMode
            }

            NetworkState.NoInternet

        } catch (e: Exception) {
            NetworkState.NoInternet
        }
    }

    private suspend fun request(url: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.requestMethod = "GET"
                connection.connect()

                connection.responseCode in 200..399
            }
        } catch (e: Exception) {
            false
        }
    }
}