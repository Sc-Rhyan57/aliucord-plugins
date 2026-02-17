package com.rhyan57.svclone

import android.content.Context
import android.util.Base64
import com.aliucord.api.SettingsAPI
import com.discord.stores.StoreStream
import com.discord.utilities.rest.RestAPI
import com.google.gson.reflect.TypeToken

object TokenManager {
    private const val TOKENS_KEY = "saved_tokens"
    private const val CURRENT_TOKEN_INDEX_KEY = "current_token_index"
    
    private var tokens = mutableListOf<String>()
    private var currentIndex = 0
    
    fun initialize(settings: SettingsAPI) {
        tokens = settings.getObject(TOKENS_KEY, mutableListOf<String>(), 
            TypeToken.getParameterized(MutableList::class.java, String::class.java).type)
        currentIndex = settings.getInt(CURRENT_TOKEN_INDEX_KEY, 0)
        
        if (tokens.isEmpty()) {
            val currentToken = getCurrentToken()
            if (currentToken != null) {
                tokens.add(currentToken)
                save(settings)
            }
        }
    }
    
    fun addToken(settings: SettingsAPI, token: String) {
        if (!tokens.contains(token)) {
            tokens.add(token)
            save(settings)
        }
    }
    
    fun removeToken(settings: SettingsAPI, token: String) {
        tokens.remove(token)
        if (currentIndex >= tokens.size) {
            currentIndex = 0
        }
        save(settings)
    }
    
    fun getTokens(): List<String> = tokens.toList()
    
    fun getNextToken(settings: SettingsAPI): String? {
        if (tokens.isEmpty()) return null
        currentIndex = (currentIndex + 1) % tokens.size
        save(settings)
        return tokens[currentIndex]
    }
    
    fun getCurrentTokenFromIndex(): String? {
        if (tokens.isEmpty()) return null
        return tokens[currentIndex]
    }
    
    fun getCurrentToken(): String? {
        return try {
            RestAPI.AppHeadersProvider.INSTANCE.authToken
        } catch (e: Exception) {
            null
        }
    }
    
    fun getTokenInfo(token: String): String {
        return try {
            val parts = token.split(".")
            if (parts.size >= 1) {
                val idBytes = Base64.decode(parts[0], Base64.DEFAULT)
                val userId = String(idBytes)
                "ID: $userId"
            } else {
                "Token inválido"
            }
        } catch (e: Exception) {
            "Token inválido"
        }
    }
    
    private fun save(settings: SettingsAPI) {
        settings.setObject(TOKENS_KEY, tokens)
        settings.setInt(CURRENT_TOKEN_INDEX_KEY, currentIndex)
    }
}
