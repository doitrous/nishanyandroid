package com.nishany.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nishany.core.*
import kotlinx.serialization.json.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val vm: StudentViewModel = viewModel()
            val ui by vm.ui.collectAsStateWithLifecycle()
            val arabic = ui.arabic ?: (LocalConfiguration.current.locales[0].language == "ar")
            CompositionLocalProvider(LocalArabic provides arabic,
                LocalLayoutDirection provides if (arabic) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                NishanyTheme { StudentApp(vm, ui) }
            }
            // Navigation only: no authentication material accepted from links.
            LaunchedEffect(ui.signedIn, ui.loading) {
                if (ui.signedIn && !ui.loading) {
                    val link = intent?.data
                    if (link?.scheme == "https" && link.host == "nishany.com") {
                        when (link.path) {
                            "/app/notebook" -> vm.page(Page.NOTEBOOK)
                            "/app/university" -> vm.page(Page.UNIVERSITY)
                        }
                        intent.data = null
                    }
                }
            }
        }
    }
}

internal val LocalArabic = staticCompositionLocalOf { false }
@Composable private fun tr(en: String, ar: String): String = if (LocalArabic.current) ar else en

@Composable fun NishanyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFFFFB3C1), secondary = Color(0xFFBBC6EB),
        background = Color(0xFF13151E), surface = Color(0xFF1C1F2A))
    else lightColorScheme(primary = Color(0xFFA82043), onPrimary = Color.White, secondary = Color(0xFF24365C),
        background = Color(0xFFFAF8F6), surface = Color(0xFFFFFDFC), surfaceVariant = Color(0xFFF0EAEB))
    MaterialTheme(colorScheme = colors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun StudentApp(vm: StudentViewModel, ui: StudentUi) {
    val arabic = LocalArabic.current
    BackHandler(ui.editor != null && !ui.loading) { vm.page(Page.NOTEBOOK) }
    Scaffold(topBar = {
        TopAppBar(title = { Text("nishany", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary) }, actions = {
            TextButton(onClick = { vm.language(!arabic) }) { Text(if (arabic) "English" else "العربية") }
        })
    }, bottomBar = {
        if (ui.signedIn && ui.editor == null) NavigationBar {
            Page.entries.forEach { page ->
                val title = pageTitle(page)
                NavigationBarItem(selected = ui.page == page, onClick = { vm.page(page) }, enabled = !ui.loading,
                    icon = { Text(title.take(1), modifier = Modifier.clearAndSetSemantics { }) }, label = { Text(title) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = 880.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    ui.error?.let { ErrorPanel(it, vm::clearMessage) }
                    if (ui.notice == "saved") Text(tr("Saved to your Nishany account.", "تم الحفظ في حسابك على نيشاني."),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    if (ui.notice == "recovery") Text(tr("If this email has an account, check its inbox for recovery instructions.",
                        "لو البريد مسجل، راجع رسائل استعادة الحساب."))
                    if (!ui.storageReady) {
                        Heading(tr("Secure storage is unavailable", "التخزين الآمن غير متاح"))
                        Text(tr("Your saved data has not been deleted. Close and reopen the app; do not clear app data if you have pending drafts.",
                            "لم تُحذف بياناتك. أغلق التطبيق وافتحه، ولا تمسح بيانات التطبيق لو عندك مسودات."))
                    } else if (ui.mfa) MfaScreen(ui, vm)
                    else if (!ui.signedIn) SignInScreen(ui, vm)
                    else if (ui.editor != null) NoteEditor(ui, vm)
                    else when (ui.page) {
                        Page.HOME -> HomeScreen(ui, vm)
                        Page.NOTEBOOK -> NotebookScreen(ui, vm)
                        Page.UNIVERSITY -> UniversityScreen(ui, vm)
                        Page.ACCOUNT -> AccountScreen(ui, vm)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable private fun pageTitle(page: Page): String = when (page) {
    Page.HOME -> tr("Home", "الرئيسية")
    Page.NOTEBOOK -> tr("Notebook", "ملاحظاتي")
    Page.UNIVERSITY -> tr("University", "الجامعة")
    Page.ACCOUNT -> tr("Account", "حسابي")
}
@Composable private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Serif,
        modifier = Modifier.semantics { heading() })
}
@Composable private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}
@Composable private fun ErrorPanel(error: Throwable, dismiss: () -> Unit) {
    val message = when (error) {
        is NoteConflict -> tr("This note changed elsewhere. Your draft is safe. Review the server copy or keep a separate copy.",
            "الملاحظة اتغيرت على جهاز تاني. مسودتك محفوظة؛ راجع نسخة الخادم أو احتفظ بنسخة منفصلة.")
        is UncertainDelivery, is NeedsReconciliation -> tr("Delivery is uncertain. Check the server before trying another save.",
            "نتيجة الحفظ غير مؤكدة. تحقق من الخادم قبل محاولة حفظ أخرى.")
        is ApiFailure -> when (error.status) {
            401 -> tr("Sign in again. Your account drafts are retained.", "سجل الدخول تاني. مسودات حسابك محفوظة.")
            402 -> tr("Your plan does not allow this request or its allowance is used up.", "الطلب خارج صلاحيات خطتك أو انتهى الحد المتاح.")
            403 -> tr("The server denied this request. Your access or session may need review.", "الخادم رفض الطلب. راجع صلاحياتك أو الجلسة.")
            404 -> tr("This item is no longer available.", "العنصر لم يعد متاحًا.")
            409 -> tr("The server found a conflict. Refresh before editing.", "في تعارض مع بيانات الخادم. حدّث قبل التعديل.")
            429 -> tr("Too many attempts. Wait ${error.retryAfter ?: "a few"} seconds before trying again.",
                "محاولات كتير. انتظر ${error.retryAfter ?: "عدة"} ثوانٍ قبل المحاولة.")
            else -> tr("The server could not complete this request.", "تعذر إتمام الطلب على الخادم.")
        }
        else -> tr("Could not finish. Check your connection or device storage; unsent edits remain on this device when draft storage succeeds.",
            "تعذر الإتمام. راجع الاتصال ومساحة الجهاز؛ التعديلات غير المرسلة تظل على الجهاز عند نجاح حفظ المسودة.")
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            TextButton(onClick = dismiss) { Text(tr("Dismiss", "إغلاق")) }
        }
    }
}

@Composable private fun SignInScreen(ui: StudentUi, vm: StudentViewModel) {
    SignInForm(ui.loading, vm::login, vm::recover, vm::refreshSession)
}

@Composable internal fun SignInForm(loading: Boolean, onLogin: (String, String) -> Unit, onRecover: (String) -> Unit, onRefresh: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") } // Never SavedStateHandle, savedInstanceState, disk, or logs.
    Heading(tr("A little progress, every day.", "خطوة لقدّام، كل يوم."))
    Text(tr("Sign in with your existing Nishany account.", "سجل الدخول بنفس حسابك على نيشاني."))
    Panel {
        OutlinedTextField(email, { email = it }, label = { Text(tr("Email", "البريد الإلكتروني")) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(password, { password = it }, label = { Text(tr("Password", "كلمة المرور")) },
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            singleLine = true, enabled = !loading, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Button(onClick = { val secret = password; password = ""; onLogin(email, secret) },
            enabled = !loading && email.isNotBlank() && password.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(tr("Sign in", "دخول"))
        }
        TextButton(onClick = { onRecover(email) }, enabled = !loading && email.isNotBlank()) {
            Text(tr("Forgot password?", "نسيت كلمة المرور؟"))
        }
        TextButton(onClick = onRefresh, enabled = !loading) { Text(tr("Check saved session", "تحقق من الجلسة المحفوظة")) }
    }
}

@Composable private fun MfaScreen(ui: StudentUi, vm: StudentViewModel) {
    var factor by remember(ui.factors) { mutableStateOf(ui.factors.firstOrNull()?.get("id").str()) }
    var code by remember { mutableStateOf("") }
    Heading(tr("Verify it’s you", "تأكيد هويتك"))
    Text(tr("Enter the code from your authenticator app.", "اكتب رمز التحقق من تطبيق المصادقة."))
    ui.factors.forEach { f ->
        FilterChip(selected = factor == f["id"].str(), onClick = { factor = f["id"].str() },
            label = { Text(f["friendlyName"].str().ifEmpty { "TOTP" }) })
    }
    OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, label = { Text(tr("6-digit code", "رمز من ٦ أرقام")) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), enabled = !ui.loading)
    Button(onClick = { vm.verify(factor, code); code = "" }, enabled = !ui.loading && code.length == 6 && factor.isNotEmpty()) {
        Text(tr("Verify", "تأكيد"))
    }
    TextButton(onClick = vm::leaveDevice, enabled = !ui.loading) { Text(tr("Use another account", "استخدم حساب تاني")) }
}

@Composable private fun HomeScreen(ui: StudentUi, vm: StudentViewModel) {
    val name = ui.me["profile"].obj()["name"].str()
    Heading(if (name.isBlank()) tr("Your next step", "خطوتك الجاية") else tr("Welcome, $name", "أهلًا، $name"))
    Text(tr("Make room for focused study.", "وقت لدراسة بتركيز."))
    OutlinedButton(onClick = vm::refreshInbox, enabled = !ui.loading) { Text(tr("Notifications", "الإشعارات")) }
    ui.inbox?.let { inbox ->
        if (inbox.isEmpty()) Text(tr("No recent notifications.", "لا توجد إشعارات حديثة."))
        inbox.forEach { notification ->
            Panel {
                Text(notification["title"].str(), style = MaterialTheme.typography.titleMedium)
                Text(notification["message"].str())
                Text(notification["createdAt"].str(), style = MaterialTheme.typography.labelSmall)
                if (!notification["read"].bool()) Text(tr("Unread", "غير مقروء"), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    Panel {
        Text(tr("YOUR NOTEBOOK", "ملاحظاتك"), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(tr("Pick up where you left off", "كمّل من مكان ما وقفت"), style = MaterialTheme.typography.titleLarge)
        if (ui.drafts.isNotEmpty()) Text(tr("${ui.drafts.size} drafts kept on this device", "${ui.drafts.size} مسودات محفوظة على الجهاز"))
        Button(onClick = { vm.page(Page.NOTEBOOK) }, enabled = !ui.loading) { Text(tr("Open notebook", "افتح ملاحظاتك")) }
    }
    Panel {
        Text(tr("YOUR UNIVERSITY", "جامعتك"), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
        Text(tr("Your modules and published timetable", "موادك والجدول المنشور"), style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { vm.page(Page.UNIVERSITY) }, enabled = !ui.loading) { Text(tr("Explore your year", "شوف السنة الدراسية")) }
    }
    if (ui.me["profile"] == JsonNull) Text(tr("Your account has no university profile yet. Enrolment is not available in this development build.",
        "حسابك لسه من غير ملف جامعي. التسجيل الجامعي مش متاح في النسخة التطويرية دي."))
}

@Composable private fun NotebookScreen(ui: StudentUi, vm: StudentViewModel) {
    var query by remember { mutableStateOf("") }
    Heading(tr("Your notebook", "ملاحظاتك"))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = vm::newNote, enabled = !ui.loading) { Text(tr("New note", "ملاحظة جديدة")) }
        OutlinedButton(onClick = vm::refreshNotes, enabled = !ui.loading) { Text(tr("Refresh", "تحديث")) }
    }
    OutlinedTextField(query, { query = it }, label = { Text(tr("Search titles, excerpts and tags", "ابحث في العناوين والمقتطفات والوسوم")) },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    val local = ui.drafts.associateBy { it.id }
    val entries = (ui.drafts.map { it.note.patch("excerpt" to string(NoteDocument.text(it.note).take(280))) } +
        ui.notes.filterNot { local.containsKey(it["id"].str()) }).filter {
        (it["title"].str() + " " + it["excerpt"].str() + " " + it["tags"].arr().joinToString { tag -> tag.str() }).contains(query, true)
    }
    if (entries.isEmpty() && !ui.loading) Text(if (query.isEmpty()) tr("No notes loaded. Create one or refresh your account.",
        "لا توجد ملاحظات محمّلة. أنشئ ملاحظة أو حدّث الحساب.") else tr("No matching notes.", "لا توجد نتائج."))
    entries.forEach { note ->
        key(note["id"].str()) {
            Panel {
                Text(note["title"].str().ifEmpty { tr("Untitled note", "ملاحظة بدون عنوان") }, style = MaterialTheme.typography.titleMedium)
                Text(note["excerpt"].str(), maxLines = 3)
                local[note["id"].str()]?.let { pending ->
                    Text(if (pending.phase == DraftPhase.EDITING) tr("Draft on this device", "مسودة على الجهاز")
                        else tr("Pending — check server", "حفظ معلق — تحقق من الخادم"), color = MaterialTheme.colorScheme.primary)
                }
                TextButton(onClick = { vm.openNote(note["id"].str()) }, enabled = !ui.loading) { Text(tr("Open note", "فتح الملاحظة")) }
            }
        }
    }
}

@Composable private fun NoteEditor(ui: StudentUi, vm: StudentViewModel) {
    val draft = ui.editor ?: return
    val note = draft.note
    var confirmDiscard by remember(draft.id) { mutableStateOf(false) }
    var tags by remember(draft.id, draft.restoredFrom) { mutableStateOf(note["tags"].arr().joinToString(", ") { it.str() }) }
    val body = NoteDocument.text(note)
    val writable = draft.phase == DraftPhase.EDITING && !ui.loading
    Heading(tr("Note", "ملاحظة"))
    TextButton(onClick = { vm.page(Page.NOTEBOOK) }, enabled = !ui.loading) { Text(tr("Back to notebook", "رجوع للملاحظات")) }
    Text(when {
        ui.localError -> tr("Draft could not be saved on this device. Keep this screen open.", "تعذر حفظ المسودة على الجهاز. خليك في الصفحة دي.")
        ui.localSaving -> tr("Saving draft on this device…", "جاري حفظ المسودة على الجهاز…")
        draft.phase != DraftPhase.EDITING -> tr("Pending write: check the server before saving.", "الحفظ معلق: تحقق من الخادم قبل الحفظ.")
        ui.dirty -> tr("Draft kept on this device. Save to sync.", "المسودة محفوظة على الجهاز. اضغط حفظ للمزامنة.")
        else -> tr("Account copy", "نسخة الحساب")
    }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(note["title"].str(), { vm.edit(it, tags, body) }, label = { Text(tr("Title", "العنوان")) },
        modifier = Modifier.fillMaxWidth(), enabled = writable, singleLine = true)
    OutlinedTextField(tags, { tags = it; vm.edit(note["title"].str(), it, body) }, label = { Text(tr("Tags, separated by commas", "الوسوم، افصل بينها بفاصلة إنجليزية")) },
        modifier = Modifier.fillMaxWidth(), enabled = writable)
    if (!NoteDocument.canEditBody(note)) Text(tr("This note contains rich formatting or embedded content. Its body is preserved; title and tags can be edited.",
        "الملاحظة فيها تنسيق غني أو محتوى مضمّن. المحتوى محفوظ؛ تقدر تعدّل العنوان والوسوم."))
    OutlinedTextField(body, { vm.edit(note["title"].str(), tags, it) }, label = { Text(tr("Note body", "المحتوى")) },
        modifier = Modifier.fillMaxWidth(), minLines = 8, readOnly = !NoteDocument.canEditBody(note) || !writable)
    if (draft.phase == DraftPhase.EDITING) {
        Button(onClick = vm::saveNote, enabled = !ui.loading && ui.dirty && !ui.localSaving && !ui.localError,
            modifier = Modifier.fillMaxWidth()) { Text(tr("Save to account", "حفظ في الحساب")) }
    } else {
        Button(onClick = vm::checkNote, enabled = !ui.loading, modifier = Modifier.fillMaxWidth()) { Text(tr("Check server copy", "تحقق من نسخة الخادم")) }
        OutlinedButton(onClick = vm::copyNote, enabled = !ui.loading) { Text(tr("Keep as a separate draft", "احتفظ بمسودة منفصلة")) }
    }
    ui.conflict?.let { remote ->
        Panel {
            Text(tr("Current server copy • version ${remote.version}", "نسخة الخادم الحالية • الإصدار ${remote.version}"), style = MaterialTheme.typography.titleSmall)
            Text(remote.note["title"].str()); Text(NoteDocument.text(remote.note))
        }
    }
    if (ui.dirty) TextButton(onClick = { confirmDiscard = true }, enabled = !ui.loading && !ui.localSaving) {
        Text(tr("Discard this local draft", "حذف المسودة المحلية دي"))
    }
    if (confirmDiscard) AlertDialog(onDismissRequest = { confirmDiscard = false },
        title = { Text(tr("Discard local edits?", "حذف التعديلات المحلية؟")) },
        text = { Text(tr("This removes only this device’s draft. It does not undo a save that may already have reached the server.",
            "ده يحذف مسودة الجهاز فقط. لا يلغي حفظًا ربما وصل للخادم بالفعل.")) },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.discardLocal() }) { Text(tr("Discard draft", "حذف المسودة")) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(tr("Cancel", "إلغاء")) } })
    if (draft.baseVersion != null) TextButton(onClick = vm::history, enabled = !ui.loading) { Text(tr("Version history", "سجل الإصدارات")) }
    ui.history?.let { versions ->
        if (versions.isEmpty()) Text(tr("No history returned.", "لا يوجد سجل متاح."))
        versions.forEach { version ->
            Panel {
                Text(version["title"].str()); Text(version["updatedAt"].str())
                Button(onClick = { vm.restore(version["id"].str()) }, enabled = !ui.loading && !ui.dirty) {
                    Text(tr("Prepare restore", "تجهيز الاستعادة"))
                }
                if (ui.dirty) Text(tr("Save the current draft before restoring another version.", "احفظ المسودة الحالية قبل استعادة إصدار تاني."))
            }
        }
    }
}

@Composable private fun UniversityScreen(ui: StudentUi, vm: StudentViewModel) {
    val uni = ui.university
    Heading(uni["university"].obj()["name"].str().ifEmpty { tr("Your university", "جامعتك") })
    Text(uni["year"].obj()["year"].str())
    OutlinedButton(onClick = vm::refreshUniversity, enabled = !ui.loading) { Text(tr("Refresh timetable", "تحديث الجدول")) }
    if (uni["status"].str() == "missing_profile") Text(tr("Set up your university on the website to view its published curriculum here.",
        "سجل جامعتك على الموقع لعرض المنهج المنشور هنا."))
    if (uni["status"].str() == "being_verified") Text(tr("Your curriculum is being verified.", "منهجك تحت المراجعة."))
    uni["terms"].arr().forEach { term ->
        Text(term.obj()["term"].str(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        term.obj()["modules"].arr().forEach { item ->
            val module = item.obj()
            Panel {
                Text(module["name"].str(), style = MaterialTheme.typography.titleLarge)
                Text(module["moduleId"].str())
                val schedule = module["schedule"].arr()
                if (schedule.isEmpty()) Text(tr("No published timetable yet.", "لا يوجد جدول منشور حاليًا."))
                schedule.forEach { row ->
                    val r = row.obj()
                    // Only fields in the server's published projection; never read raw admin schedules.
                    Text(listOf("title", "date", "weekday", "startTime", "endTime", "location", "group").map { r[it].str() }.filter { it.isNotBlank() }.joinToString(" · "))
                }
            }
        }
    }
}

@Composable private fun AccountScreen(ui: StudentUi, vm: StudentViewModel) {
    var confirm by remember { mutableStateOf(false) }
    Heading(tr("Your account", "حسابك"))
    Panel {
        Text(ui.me["profile"].obj()["name"].str(), style = MaterialTheme.typography.titleLarge)
        Text(ui.me["user"].obj()["email"].str())
        Text(tr("Plan: ", "الخطة: ") + ui.me["entitlement"].obj()["plan"].str())
        val end = ui.me["entitlement"].obj()["expiresAt"].str()
        if (end.isNotEmpty()) Text(tr("Expires: ", "تنتهي: ") + end)
        Text(tr("Purchases and Google Play restoration are not enabled in this development build.",
            "المشتريات واستعادة اشتراكات Google Play مش متاحة في النسخة التطويرية دي."))
    }
    OutlinedButton(onClick = vm::leaveDevice, enabled = !ui.loading) { Text(tr("Remove sign-in from this device", "إزالة الدخول من هذا الجهاز")) }
    Text(tr("This removes the local session. It does not revoke sessions on the server. Drafts stay encrypted for this account.",
        "ده بيزيل الجلسة المحلية، ولا يلغي جلسات الخادم. المسودات تفضل مشفّرة للحساب ده."))
    TextButton(onClick = { confirm = true }, enabled = !ui.loading) { Text(tr("Sign out on all devices", "تسجيل الخروج من كل الأجهزة")) }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(tr("Sign out everywhere?", "تسجيل الخروج من كل الأجهزة؟")) },
        text = { Text(tr("Nishany’s server will also sign you out on the website and iOS. Your local drafts remain encrypted.",
            "خادم نيشاني هيسجل خروجك من الموقع وiOS كمان. المسودات المحلية تفضل مشفّرة.")) },
        confirmButton = { TextButton(onClick = { confirm = false; vm.logoutEverywhere() }) { Text(tr("Sign out everywhere", "خروج من كل الأجهزة")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("Cancel", "إلغاء")) } })
}
