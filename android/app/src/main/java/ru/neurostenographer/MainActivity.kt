package ru.neurostenographer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private var cancelled = AtomicBoolean(false)
    private lateinit var status: TextView
    private lateinit var body: TextView
    private lateinit var select: Button
    private lateinit var export: Button
    private lateinit var cancel: Button
    private var json: String? = null
    private var busy = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28,32,28,20) }
        layout.addView(TextView(this).apply { text = "Нейро-стенографист"; textSize = 25f })
        layout.addView(TextView(this).apply { text = "Всё на устройстве • WAV / MP3 / M4A\nДо 10 минут. Модели включены в APK."; textSize = 14f })
        select = Button(this).apply { text = "Импортировать аудио"; setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "audio/*"; addCategory(Intent.CATEGORY_OPENABLE) }, 1)
        } }; layout.addView(select)
        cancel = Button(this).apply { text = "Отменить"; isEnabled = false; setOnClickListener {
            cancelled.set(true); status.text = "Отмена после текущего шага модели…"; isEnabled = false
        } }; layout.addView(cancel)
        export = Button(this).apply { text = "Экспорт JSON"; isEnabled = false; setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/json"; addCategory(Intent.CATEGORY_OPENABLE); putExtra(Intent.EXTRA_TITLE,"transcript.json") }, 2)
        } }; layout.addView(export)
        status = TextView(this).apply { text = "Выберите запись. Интернет не требуется."; textSize = 15f }; layout.addView(status)
        body = TextView(this).apply { textSize = 17f; setTextIsSelectable(true); setPadding(0,20,0,0) }
        layout.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(layout)
        // Respect system bars on target SDK 35.
        layout.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(28, insets.systemWindowInsetTop + 20, 28, insets.systemWindowInsetBottom + 16); insets
        }
        val previous = File(filesDir,"last-transcript.json")
        if (previous.isFile) { json = previous.readText(); body.text = json; export.isEnabled = true; status.text = "Предыдущий результат сохранён" }
    }
    @Deprecated("Activity result API used to keep dependencies small")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == 1) process(uri)
        if (requestCode == 2) {
            val content = json ?: return
            executor.execute {
                try {
                    contentResolver.openOutputStream(uri,"wt").use { out -> requireNotNull(out); out.write(content.toByteArray(Charsets.UTF_8)) }
                    ui { Toast.makeText(this,"JSON сохранён",Toast.LENGTH_LONG).show() }
                } catch (e: Exception) { ui { error(e) } }
            }
        }
    }
    private fun ui(action: () -> Unit) { runOnUiThread { if (!isFinishing && !isDestroyed) action() } }
    private fun setBusy(value: Boolean) {
        busy = value; select.isEnabled = !value; cancel.isEnabled = value; export.isEnabled = !value && json != null
        if (value) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun process(uri: Uri) {
        if (busy) return
        cancelled = AtomicBoolean(false)
        setBusy(true)
        val token = cancelled
        executor.execute {
            try {
                val result = Pipeline(applicationContext, token) { message -> ui { if (!token.get()) status.text = message } }.run(uri)
                val output = Transcript.json(result.segments)
                val temp = File(filesDir,"last-transcript.tmp")
                temp.writeText(output)
                check(temp.renameTo(File(filesDir,"last-transcript.json"))) { "Не удалось сохранить результат" }
                ui {
                    json = output
                    body.text = result.segments.joinToString("\n\n") { "${it.speakerId}  ${it.startMs}–${it.endMs} мс\n${it.text}" }
                    val rtf = result.elapsedMs.toDouble() / result.durationMs.coerceAtLeast(1)
                    status.text = "Готово: ${result.segments.size} реплик • ${result.elapsedMs / 1000} с • RTF %.2f".format(rtf) +
                        if (result.warnings.isEmpty()) "" else "\n" + result.warnings.joinToString("\n")
                }
            } catch (e: CancellationException) { ui { status.text = "Обработка отменена. Предыдущий результат сохранён." } }
            catch (e: Exception) { android.util.Log.e("NeuroStenographer","Processing failed",e); ui { error(e) } }
            catch (e: LinkageError) { android.util.Log.e("NeuroStenographer","Native runtime failed",e); ui { error(e) } }
            finally { ui { setBusy(false) } }
        }
    }
    private fun error(e: Throwable) { status.text = "Ошибка: ${e.message ?: e.javaClass.simpleName}" }
    override fun onDestroy() { cancelled.set(true); executor.shutdownNow(); super.onDestroy() }
    @Deprecated("Legacy back handling")
    override fun onBackPressed() {
        if (!busy) super.onBackPressed() else AlertDialog.Builder(this).setMessage("Прервать обработку и выйти?")
            .setNegativeButton("Остаться",null).setPositiveButton("Выйти") { _,_ -> cancelled.set(true); finish() }.show()
    }
}
