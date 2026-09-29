package com.nishany.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.nishany.core.*
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*

/** AES-GCM authenticates the storage key as AAD. Key material never leaves Android Keystore. */
class SecureVault(context: Context, namespace: String = "vault-v1") {
    init { require(Regex("[a-z0-9-]+").matches(namespace)) }
    private val dir = File(context.noBackupFilesDir, namespace).apply { mkdirs() }
    private val alias = "nishany.local.$namespace"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun file(name: String): AtomicFile {
        val hash = MessageDigest.getInstance("SHA-256").digest(name.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(dir, hash))
    }
    @Synchronized fun read(name: String): String? {
        val f = file(name)
        if (!f.baseFile.exists() && !File(f.baseFile.path + ".bak").exists()) return null
        val raw = f.readFully()
        require(raw.size >= 29 && raw[0] == 1.toByte()) { "Invalid encrypted record" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(1, 13)))
        cipher.updateAAD(name.toByteArray())
        return cipher.doFinal(raw.copyOfRange(13, raw.size)).toString(Charsets.UTF_8)
    }
    @Synchronized fun write(name: String, value: String?) {
        val f = file(name)
        if (value == null) { f.delete(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(name.toByteArray())
        require(cipher.iv.size == 12)
        val raw = byteArrayOf(1) + cipher.iv + cipher.doFinal(value.toByteArray())
        val stream = f.startWrite()
        try { stream.write(raw); f.finishWrite(stream) } catch (e: Exception) { f.failWrite(stream); throw e }
    }
}

/** Cookie jar belongs to ONE session generation; deactivated responses cannot restore old cookies. */
class SessionCookies(private val vault: SecureVault) : CookieJar {
    private var active = true
    private var values = mutableListOf<Cookie>()
    init {
        val origin = okhttp3.HttpUrl.Builder().scheme("https").host("nishany.com").build()
        values = vault.read("session.cookies")?.let { raw ->
            WireJson.parseToJsonElement(raw).arr().mapNotNull { Cookie.parse(origin, it.str()) }.toMutableList()
        } ?: mutableListOf()
    }
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!active || !url.isHttps || url.host != "nishany.com") return
        cookies.filter { it.name == "nsid" && it.matches(url) }.forEach { cookie ->
            values.removeAll { it.name == cookie.name }
            if (cookie.expiresAt > System.currentTimeMillis()) values.add(cookie)
        }
        vault.write("session.cookies", JsonArray(values.map { string(it.toString()) }).toString())
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (!active || !url.isHttps || url.host != "nishany.com") emptyList()
        else values.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
    @Synchronized fun clearAndDeactivate() {
        active = false; values.clear(); vault.write("session.cookies", null)
    }
}

class EncryptedDrafts(private val vault: SecureVault) : DraftStorage {
    private val monitor = Any()
    private fun key(owner: String) = "drafts:$owner"
    private fun read(owner: String): List<NoteDraft> = vault.read(key(owner))?.let { raw ->
        WireJson.parseToJsonElement(raw).arr().map { NoteDraft.decode(it.toString()) }
    }.orEmpty().also { require(it.all { d -> d.owner == owner }) }
    override suspend fun list(owner: String): List<NoteDraft> = withContext(Dispatchers.IO) { synchronized(monitor) { read(owner) } }
    override suspend fun put(draft: NoteDraft) = withContext(Dispatchers.IO) {
        synchronized(monitor) {
            val all = read(draft.owner).filterNot { it.id == draft.id } + draft
            vault.write(key(draft.owner), JsonArray(all.map { WireJson.parseToJsonElement(it.encode()) }).toString())
        }
    }
    override suspend fun remove(owner: String, id: String) = withContext(Dispatchers.IO) {
        synchronized(monitor) {
            val all = read(owner).filterNot { it.id == id }
            vault.write(key(owner), if (all.isEmpty()) null else JsonArray(all.map { WireJson.parseToJsonElement(it.encode()) }).toString())
        }
    }
}
