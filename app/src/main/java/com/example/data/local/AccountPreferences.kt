package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.accountDataStore by preferencesDataStore(name = "nico_account_prefs")

data class NicoAccount(
    val userSession: String = "",
    val userId: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
    val isPremium: Boolean = false
) {
    val isLoggedIn: Boolean
        get() = userSession.isNotBlank()
}

class AccountPreferences(private val context: Context) {
    private object Keys {
        val USER_SESSION = stringPreferencesKey("user_session")
        val USER_ID = stringPreferencesKey("user_id")
        val NICKNAME = stringPreferencesKey("nickname")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
    }

    val accountFlow: Flow<NicoAccount> = context.accountDataStore.data.map { prefs ->
        NicoAccount(
            userSession = prefs[Keys.USER_SESSION].orEmpty(),
            userId = prefs[Keys.USER_ID].orEmpty(),
            nickname = prefs[Keys.NICKNAME].orEmpty(),
            avatarUrl = prefs[Keys.AVATAR_URL].orEmpty(),
            isPremium = prefs[Keys.IS_PREMIUM] ?: false
        )
    }

    suspend fun saveAccount(account: NicoAccount) {
        context.accountDataStore.edit { prefs ->
            prefs[Keys.USER_SESSION] = account.userSession
            prefs[Keys.USER_ID] = account.userId
            prefs[Keys.NICKNAME] = account.nickname
            prefs[Keys.AVATAR_URL] = account.avatarUrl
            prefs[Keys.IS_PREMIUM] = account.isPremium
        }
    }

    suspend fun clearAccount() {
        context.accountDataStore.edit { it.clear() }
    }
}
