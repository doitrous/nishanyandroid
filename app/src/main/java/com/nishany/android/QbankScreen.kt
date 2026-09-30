package com.nishany.android

import android.text.Html
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nishany.core.*

/** Native text presentation. Embedded media/complex math need a richer renderer before sitting tests. */
@Composable private fun QuestionText(value: String) {
    Text(Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT).toString())
}

@Composable internal fun QbankScreen(ui: StudentUi, vm: StudentViewModel) {
    Text(tr("Question bank", "بنك الأسئلة"), style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() })
    Text(tr("Browse your published question catalogue and saved work. Taking and submitting tests is not available in this build.",
        "تصفح الأسئلة المنشورة وجلساتك المحفوظة. حل الاختبارات وتسليمها غير متاحين في هذه النسخة."))
    if (ui.qbankQuestions.isNotEmpty()) {
        val question = ui.qbankQuestions[ui.qbankIndex]
        val saved = ui.qbankSaved.obj()
        TextButton(onClick = vm::closeQbankViewer, enabled = !ui.loading) { Text(tr("Back to catalogue", "العودة للقائمة")) }
        Text(tr("Saved session • read only", "جلسة محفوظة • للقراءة فقط"), style = MaterialTheme.typography.titleMedium)
        Text("${ui.qbankIndex + 1} / ${ui.qbankQuestions.size}")
        Text(tr("Simplified text view; complex formatting, equations and embedded images may be incomplete.",
            "عرض نصي مبسط؛ قد لا تظهر التنسيقات المعقدة والمعادلات والصور المضمّنة كاملة."))
        QuestionText(question.raw["fields"].obj()["Vignette"].str())
        QuestionText(question.raw["title"].str())
        if (question.data["attachedImage"].str().isNotBlank() || question.data["attachments"].arr().isNotEmpty()) {
            Text(tr("This question has media. Open the website to see all images and attachments before answering.",
                "السؤال يحتوي على وسائط. افتح الموقع لعرض الصور والمرفقات كاملة قبل الإجابة."))
        }
        val selected = saved["answers"].obj()[question.id].long()?.toInt()
        question.options.forEachIndexed { i, option ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selected == i) Text(tr("Your saved answer", "إجابتك المحفوظة"), style = MaterialTheme.typography.labelLarge)
                    QuestionText(option["text"].str())
                }
            }
        }
        // Never reveal the key for an unchecked or timed question.
        if (saved["checked"].obj()[question.id].bool() && saved["mode"].str() == "tutor") {
            Text(tr("Explanation", "الشرح"), style = MaterialTheme.typography.titleLarge)
            QuestionText(question.explanation)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { vm.qbankIndex(ui.qbankIndex - 1) }, enabled = ui.qbankIndex > 0 && !ui.loading) {
                Text(tr("Previous", "السابق"))
            }
            Button(onClick = { vm.qbankIndex(ui.qbankIndex + 1) }, enabled = ui.qbankIndex < ui.qbankQuestions.lastIndex && !ui.loading) {
                Text(tr("Next", "التالي"))
            }
        }
        return
    }
    OutlinedButton(onClick = vm::refreshQbank, enabled = !ui.loading) { Text(tr("Refresh catalogue", "تحديث القائمة")) }
    val saved = ui.qbankSaved
    val finished = ui.qbankHistory.orEmpty().any { it["id"].str() == saved.obj()["sessionId"].str() }
    if (QbankSession.resumable(saved) && !finished) {
        Text(saved.obj()["name"].str().ifBlank { tr("Saved session", "جلسة محفوظة") }, style = MaterialTheme.typography.titleLarge)
        Text(tr("Opening loads the exact saved questions and may use your weekly question allowance.",
            "فتح الجلسة يحمّل أسئلتها المحفوظة وقد يستهلك من حد الأسئلة الأسبوعي."))
        Button(onClick = vm::viewSavedQbank, enabled = !ui.loading) { Text(tr("View saved session", "عرض الجلسة المحفوظة")) }
    }
    var query by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("") }
    OutlinedTextField(query, { query = it }, label = { Text(tr("Filter topic or subject", "تصفية الموضوع أو المادة")) },
        modifier = Modifier.fillMaxWidth(), singleLine = true)
    // Vertical chips remain usable with large text and narrow/RTL screens.
    listOf("", "Easy", "Moderate", "Hard").forEach { value ->
        val label = when (value) {
            "Easy" -> tr("Easy", "سهل")
            "Moderate" -> tr("Moderate", "متوسط")
            "Hard" -> tr("Hard", "صعب")
            else -> tr("All difficulties", "كل المستويات")
        }
        FilterChip(selected = difficulty == value, onClick = { difficulty = value }, label = { Text(label) })
    }
    ui.catalogue?.let { catalogue ->
        val visible = catalogue.filter { (difficulty.isEmpty() || it.difficulty == difficulty) &&
            (query.isBlank() || it.topic.contains(query, true) || it.subject.contains(query, true)) }
        Text(tr("${visible.size} questions", "${visible.size} سؤال"))
        if (visible.isEmpty()) Text(tr("No questions match these filters.", "لا توجد أسئلة تطابق التصفية."))
        val groups = visible.groupBy { it.subject to it.topic }.entries.sortedBy { it.key.second }
        var limit by remember(query, difficulty) { mutableIntStateOf(30) }
        groups.take(limit).forEach { (group, questions) ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(group.second, style = MaterialTheme.typography.titleMedium)
                    Text(group.first)
                    Text(tr("${questions.size} published questions", "${questions.size} سؤال منشور"))
                }
            }
        }
        if (groups.size > limit) TextButton(onClick = { limit += 30 }) { Text(tr("Show more topics", "عرض موضوعات أخرى")) }
    }
    Text(tr("Previous tests", "الاختبارات السابقة"), style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() })
    ui.qbankHistory?.let { history ->
        if (history.isEmpty()) Text(tr("No saved test history.", "لا يوجد سجل اختبارات محفوظ."))
        var limit by remember(history) { mutableIntStateOf(20) }
        history.sortedByDescending { it["finishedAt"].str() }.take(limit).forEach { sitting ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sitting["name"].str().ifBlank { sitting["kind"].str() })
                    Text(sitting["finishedAt"].str())
                    val result = sitting["result"].obj()
                    if (result["correct"].long() != null) Text(tr("Correct: ", "الصحيح: ") +
                        "${result["correct"].long()} / ${result["total"].long() ?: "—"}")
                }
            }
        }
        if (history.size > limit) TextButton(onClick = { limit += 20 }) { Text(tr("Show more tests", "عرض اختبارات أخرى")) }
    }
}
