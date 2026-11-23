package com.fixora.app.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// El nombre del archivo (datastore/session.preferences_pb) se excluye de los respaldos en
// res/xml/backup_rules.xml y data_extraction_rules.xml.
private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")
private val SESSION_KEY = stringPreferencesKey("session")

/** Guarda la sesión cifrada en el celular para no pedir el login cada vez que se abre la app. */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: SessionCipher,
    private val json: Json,
) : SessionTokens {

    private val dataStore = context.sessionDataStore

    /** Emite la sesión actual, o null si no hay nadie con sesión iniciada. */
    val session: Flow<Session?> = dataStore.data
        .map { prefs -> prefs[SESSION_KEY]?.let(::decode) }
        .distinctUntilChanged()

    override suspend fun current(): Session? = session.first()

    suspend fun save(session: Session) {
        val encrypted = cipher.encrypt(json.encodeToString(Session.serializer(), session))
        dataStore.edit { it[SESSION_KEY] = encrypted }
    }

    override suspend fun updateTokens(accessToken: String, refreshToken: String) {
        val session = current() ?: return
        save(session.copy(accessToken = accessToken, refreshToken = refreshToken))
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(SESSION_KEY) }
    }

    // Si no se puede descifrar o leer (llave borrada, formato viejo), se trata como sin sesión.
    private fun decode(stored: String): Session? = cipher.decrypt(stored)?.let { plain ->
        runCatching { json.decodeFromString(Session.serializer(), plain) }.getOrNull()
    }
}
