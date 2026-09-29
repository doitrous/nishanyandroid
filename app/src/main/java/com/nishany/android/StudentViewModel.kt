package com.nishany.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nishany.core.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

enum class Page { HOME, NOTEBOOK, UNIVERSITY, ACCOUNT }
data class StudentUi(
    val loading: Boolean = true, val signedIn: Boolean = false, val mfa: Boolean = false,
    val error: Throwable? = null, val notice: String? = null, val storageReady: Boolean = true,
    val me: JsonObject = JsonObject(emptyMap()), val university: JsonObject = JsonObject(emptyMap()),
    val notes: List<JsonObject> = emptyList(), val drafts: List<NoteDraft> = emptyList(),
    val page: Page = Page.HOME, val editor: NoteDraft? = null, val dirty: Boolean = false,
    val localSaving: Boolean = false, val localError: Boolean = false,
    val conflict: NoteSnapshot? = null, val history: List<JsonObject>? = null,
    val factors: List<JsonObject> = emptyList(), val arabic: Boolean? = null,
    val inbox: List<JsonObject>? = null,
)

class StudentViewModel(application: Application) : AndroidViewModel(application) {
    private val state = MutableStateFlow(StudentUi())
    val ui = state.asStateFlow()
    private var generation = 0L
    private var editRevision = 0L
    private lateinit var vault: SecureVault
    private lateinit var disk: EncryptedDrafts
    private var cookies: SessionCookies? = null
    private var api: NishanyApi? = null
    private var notes: NotebookRepository? = null
    private val diskQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        viewModelScope.launch { for (work in diskQueue) work() }
        try {
            vault = SecureVault(application); disk = EncryptedDrafts(vault)
            newConnection(); refreshSession()
        } catch (e: Exception) {
            state.value = StudentUi(loading = false, error = e, storageReady = false)
        }
    }
    private fun newConnection() {
        cookies = SessionCookies(vault)
        api = NishanyApi(requireNotNull(cookies))
    }
    private fun publish(g: Long, change: (StudentUi) -> StudentUi) { if (g == generation) state.value = change(state.value) }
    private fun task(work: suspend (Long, NishanyApi) -> Unit) {
        val client = api ?: return
        if (state.value.loading) return
        val g = generation
        publish(g) { it.copy(loading = true, error = null, notice = null) }
        viewModelScope.launch {
            try { work(g, client) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                publish(g) { it.copy(error = e) }
                if (e is ApiFailure && e.status == 401 && g == generation) {
                    // Preserve owner-scoped drafts; remove expired credentials and all visible account data.
                    leaveDevice()
                    state.value = state.value.copy(error = e)
                }
            } finally { publish(g) { it.copy(loading = false) } }
        }
    }
    fun refreshSession() {
        state.value = state.value.copy(loading = false)
        task { g, client -> loadAccount(g, client) }
    }
    private suspend fun loadAccount(g: Long, client: NishanyApi) {
        val accounts = AccountRepository(client)
        val user = accounts.session()
        if (user["id"].str().isEmpty()) { publish(g) { StudentUi(loading = true, arabic = it.arabic) }; return }
        if (user["mfaRequired"].bool() && user["aal"].str() != "aal2") {
            val factors = accounts.factors()
            publish(g) { it.copy(mfa = true, factors = factors) }; return
        }
        val me = accounts.me()
        if (me["user"].obj()["mfaPending"].bool()) {
            val factors = accounts.factors()
            publish(g) { it.copy(mfa = true, factors = factors) }; return
        }
        require(me["user"].obj()["id"].str() == user["id"].str())
        val repo = NotebookRepository(client, disk, user["id"].str())
        val pending = repo.drafts()
        if (g != generation) return
        notes = repo
        publish(g) { it.copy(signedIn = true, mfa = false, me = me, drafts = pending) }
    }
    fun login(email: String, password: String) = task { g, client ->
        val accounts = AccountRepository(client)
        val mfa = accounts.login(email, password)
        if (mfa) {
            val factors = accounts.factors()
            publish(g) { it.copy(mfa = true, factors = factors) }
        } else loadAccount(g, client)
    }
    fun verify(factor: String, code: String) = task { g, client ->
        AccountRepository(client).verifyTotp(factor, code); loadAccount(g, client)
    }
    fun recover(email: String) = task { g, client ->
        AccountRepository(client).recover(email)
        publish(g) { it.copy(notice = "recovery") }
    }
    fun language(arabic: Boolean) { state.value = state.value.copy(arabic = arabic) }
    fun clearMessage() { state.value = state.value.copy(error = null, notice = null) }
    fun page(page: Page) {
        if (state.value.loading) return
        if (page == Page.NOTEBOOK && !Entitlements.canOpenNotebook(state.value.me)) {
            state.value = state.value.copy(error = ApiFailure(402, json("error" to string("subscription_required"))))
            return
        }
        state.value = state.value.copy(page = page, editor = null, history = null, conflict = null, error = null)
        when (page) {
            Page.NOTEBOOK -> refreshNotes()
            Page.UNIVERSITY -> refreshUniversity()
            Page.ACCOUNT -> task { g, c -> val me = AccountRepository(c).me(); publish(g) { it.copy(me = me) } }
            else -> Unit
        }
    }
    fun refreshNotes() {
        val repo = notes ?: return
        task { g, _ ->
            val pending = repo.drafts(); publish(g) { it.copy(drafts = pending) }
            val remote = repo.list(); publish(g) { it.copy(notes = remote) }
        }
    }
    fun refreshUniversity() = task { g, client ->
        val result = AccountRepository(client).university(); publish(g) { it.copy(university = result) }
    }
    fun refreshInbox() = task { g, client ->
        val result = NotificationRepository(client).inbox(); publish(g) { it.copy(inbox = result) }
    }
    fun newNote() {
        val repo = notes ?: return
        if (state.value.loading) return
        val draft = NoteDraft(repo.owner, NoteDocument.create(), null)
        state.value = state.value.copy(editor = draft, dirty = true, history = null, error = null, conflict = null)
        persist(draft)
    }
    fun openNote(id: String) {
        val repo = notes ?: return
        task { g, _ ->
            val pending = repo.drafts().find { it.id == id }
            if (pending != null) {
                publish(g) { it.copy(editor = pending, dirty = true, localSaving = false, history = null, conflict = null) }
            } else {
                val found = repo.read(id) ?: throw ApiFailure(404, json("error" to string("not_found")))
                publish(g) { it.copy(editor = NoteDraft(repo.owner, found.note, found.version), dirty = false,
                    localSaving = false, localError = false, history = null, conflict = null) }
            }
        }
    }
    fun edit(title: String, tags: String, body: String) {
        if (state.value.loading) return
        val draft = state.value.editor ?: return
        if (draft.phase != DraftPhase.EDITING) return
        try {
            val edited = draft.copy(note = NoteDocument.edit(draft.note, title,
                tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }, body))
            state.value = state.value.copy(editor = edited, dirty = true, error = null)
            persist(edited)
        } catch (e: Exception) { state.value = state.value.copy(error = e) }
    }
    private fun persist(draft: NoteDraft) {
        val repo = notes ?: return
        val g = generation
        val revision = ++editRevision
        publish(g) { it.copy(localSaving = true, localError = false) }
        diskQueue.trySend {
            try {
                repo.stage(draft)
                val pending = repo.drafts()
                publish(g) { if (revision == editRevision) it.copy(localSaving = false, localError = false, drafts = pending) else it }
            } catch (e: Exception) {
                publish(g) { it.copy(localSaving = false, localError = true, error = e) }
            }
        }
    }
    private suspend fun flushDisk() {
        val barrier = CompletableDeferred<Unit>()
        diskQueue.send { barrier.complete(Unit) }; barrier.await()
    }
    fun saveNote() {
        val repo = notes ?: return
        val draft = state.value.editor ?: return
        task { g, _ ->
            flushDisk()
            try {
                val saved = repo.save(draft)
                val pending = repo.drafts()
                publish(g) { it.copy(editor = NoteDraft(repo.owner, saved.note, saved.version), dirty = false,
                    drafts = pending, conflict = null, notice = "saved") }
            } catch (e: Exception) {
                val pending = repo.drafts()
                publish(g) { it.copy(editor = pending.find { d -> d.id == draft.id } ?: it.editor,
                    drafts = pending, conflict = (e as? NoteConflict)?.current) }
                throw e
            }
        }
    }
    fun checkNote() {
        val repo = notes ?: return
        val draft = state.value.editor ?: return
        task { g, _ ->
            try {
                val confirmed = repo.reconcile(draft.id)
                val pending = repo.drafts()
                publish(g) { it.copy(editor = confirmed?.let { s -> NoteDraft(repo.owner, s.note, s.version) },
                    drafts = pending, dirty = false, conflict = null, notice = "saved") }
            } catch (e: NoteConflict) {
                val pending = repo.drafts()
                publish(g) { it.copy(editor = pending.find { d -> d.id == draft.id }, drafts = pending, conflict = e.current) }
                throw e
            }
        }
    }
    fun copyNote() {
        if (state.value.loading) return
        val draft = state.value.editor ?: return
        val copy = NoteDraft(draft.owner, draft.note.patch("id" to string("nb-${UUID.randomUUID()}")), null)
        state.value = state.value.copy(editor = copy, dirty = true, conflict = null, history = null, error = null)
        persist(copy) // The original pending entry is deliberately retained.
    }
    fun history() {
        val repo = notes ?: return
        val draft = state.value.editor ?: return
        task { g, _ -> val versions = repo.versions(draft.id); publish(g) { it.copy(history = versions) } }
    }
    fun restore(versionId: String) {
        val repo = notes ?: return
        val draft = state.value.editor ?: return
        if (state.value.dirty) return
        task { g, _ ->
            val old = repo.version(draft.id, versionId)
            val restored = draft.copy(note = old.patch("id" to string(draft.id)), restoredFrom = versionId)
            repo.stage(restored)
            publish(g) { it.copy(editor = restored, dirty = true, history = null) }
        }
    }
    fun discardLocal() {
        val repo = notes ?: return
        val id = state.value.editor?.id ?: return
        task { g, _ ->
            flushDisk(); repo.discardLocal(id)
            val pending = repo.drafts()
            publish(g) { it.copy(editor = null, drafts = pending, dirty = false, conflict = null, history = null) }
        }
    }
    fun leaveDevice() {
        generation++
        val language = state.value.arabic
        state.value = StudentUi(loading = false, arabic = language)
        try {
            cookies?.clearAndDeactivate(); api?.close(); notes = null; newConnection()
        } catch (e: Exception) {
            api?.close(); api = null; notes = null
            state.value = state.value.copy(storageReady = false, error = e)
        }
    }
    fun logoutEverywhere() = task { g, client ->
        AccountRepository(client).logoutEverywhere()
        if (g == generation) leaveDevice()
    }
    override fun onCleared() { api?.close(); diskQueue.close(); super.onCleared() }
}
