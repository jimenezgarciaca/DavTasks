package org.tasks.quickadd

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.tasks.R
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.Task
import org.tasks.databinding.SheetQuickAddBinding
import org.tasks.preferences.Device
import org.tasks.preferences.Preferences
import org.tasks.repeats.Frequency
import org.tasks.time.DateTime
import java.text.DateFormat
import java.util.Locale
import timber.log.Timber
import javax.inject.Inject

/**
 * Hoja inferior para crear tareas escribiendo o dictando una línea con marcadores
 * (`#etiqueta @mañana +Lista ! *semanal`). Muestra en vivo lo que se va a interpretar.
 * "Añadir" crea la tarea; "Más detalles" abre el formulario ya relleno.
 */
@AndroidEntryPoint
class QuickAddSheet : BottomSheetDialogFragment() {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var device: Device
    @Inject lateinit var caldavDao: CaldavDao
    @Inject lateinit var mistral: MistralClient

    private var binding: SheetQuickAddBinding? = null
    private var listNames: List<String> = emptyList()
    private lateinit var syntax: QuickAddSyntax

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { result.resultCode == Activity.RESULT_OK && it.isNotBlank() }
            ?: return@registerForActivityResult
        binding?.quickAddInput?.let { input ->
            val current = input.text.toString().trimEnd()
            if (mistral.isConfigured && current.isEmpty()) {
                // Lo dictado es lenguaje natural: se interpreta y se deja en la caja para revisarlo.
                input.setText(text.replaceFirstChar { it.uppercase() })
                interpret()
                return@let
            }
            val spoken = if (current.isEmpty()) text.replaceFirstChar { it.uppercase() } else text
            input.setText(if (current.isEmpty()) spoken else "$current $spoken")
            input.setSelection(input.text.length)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        syntax = preferences.quickAddSyntax
        val binding = SheetQuickAddBinding.inflate(inflater, container, false)
        this.binding = binding
        with(binding) {
            quickAddInput.hint = getString(
                R.string.quick_add_input_hint,
                syntax.tag.toString(),
                syntax.date.toString(),
                syntax.priority.toString(),
            )
            quickAddInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = updatePreview()
            })
            quickAddInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    submit(openEditor = false)
                    true
                } else {
                    false
                }
            }
            quickAddAi.isVisible = mistral.isConfigured
            quickAddAi.setOnClickListener { interpret() }
            quickAddVoice.isVisible = device.voiceInputAvailable()
            quickAddVoice.setOnClickListener {
                speech.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_create_prompt))
                )
            }
            quickAddHelp.text = syntax.helpLines(spanish = Locale.getDefault().language == "es")
                .joinToString("\n")
            quickAddHelpToggle.setOnClickListener { quickAddHelp.isVisible = !quickAddHelp.isVisible }
            quickAddSave.setOnClickListener { submit(openEditor = false) }
            quickAddDetails.setOnClickListener { submit(openEditor = true) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            listNames = caldavDao.getCalendars().mapNotNull { it.name }
            updatePreview()
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        binding?.quickAddInput?.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    /** Envía el texto a Mistral y lo sustituye por la línea con marcadores que devuelve. */
    private fun interpret() {
        val binding = binding ?: return
        val text = binding.quickAddInput.text.toString().trim()
        if (text.isEmpty()) return
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = mistral.interpret(text)
                this@QuickAddSheet.binding?.quickAddInput?.let { input ->
                    input.setText(result)
                    input.setSelection(input.text.length)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                val reason = e.message?.takeIf { it.isNotBlank() }
                    ?: getString(R.string.quick_add_ai_unrecognized)
                Toast.makeText(requireContext(), getString(R.string.quick_add_ai_error, reason), Toast.LENGTH_LONG).show()
            } finally {
                setBusy(false)
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        val binding = binding ?: return
        binding.quickAddProgress.isVisible = busy
        listOf(binding.quickAddInput, binding.quickAddAi, binding.quickAddVoice, binding.quickAddSave, binding.quickAddDetails)
            .forEach { it.isEnabled = !busy }
    }

    private fun submit(openEditor: Boolean) {
        val text = binding?.quickAddInput?.text?.toString()?.trim().orEmpty()
        if (text.isEmpty() && !openEditor) {
            dismiss()
            return
        }
        setFragmentResult(REQUEST_KEY, bundleOf(EXTRA_TEXT to text, EXTRA_OPEN_EDITOR to openEditor))
        dismiss()
    }

    private fun updatePreview() {
        val binding = binding ?: return
        val text = binding.quickAddInput.text.toString()
        val now = DateTime()
        val result = parseQuickAdd(
            text = text,
            today = LocalDate(now.year, now.monthOfYear, now.dayOfMonth),
            now = LocalTime(now.hourOfDay, now.minuteOfHour),
            syntax = syntax,
        )
        val parts = buildList {
            result.dueDate?.let { date ->
                val time = result.dueTime
                val millis = DateTime(date.year, date.monthNumber, date.dayOfMonth, time?.hour ?: 12, time?.minute ?: 0).millis
                val formatted = if (time != null) {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(millis)
                } else {
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(millis)
                }
                add(getString(R.string.quick_add_due, formatted))
            }
            result.recurrence?.let { recur ->
                val frequency = when (recur.frequency) {
                    Frequency.DAILY -> getString(R.string.quick_add_daily)
                    Frequency.WEEKLY -> getString(R.string.quick_add_weekly)
                    Frequency.MONTHLY -> getString(R.string.quick_add_monthly)
                    Frequency.YEARLY -> getString(R.string.quick_add_yearly)
                    else -> null
                }
                add(listOfNotNull(getString(R.string.quick_add_repeats), frequency).joinToString(" · "))
            }
            result.tags.forEach { add("${syntax.tag}$it") }
            result.list?.let { name ->
                val exists = listNames.any { it.equals(name, ignoreCase = true) }
                add(getString(if (exists) R.string.quick_add_list else R.string.quick_add_list_unknown, name))
            }
            result.priority?.let {
                add(
                    getString(
                        when (it) {
                            Task.Priority.HIGH -> R.string.quick_add_priority_high
                            Task.Priority.MEDIUM -> R.string.quick_add_priority_medium
                            Task.Priority.LOW -> R.string.quick_add_priority_low
                            else -> R.string.quick_add_priority_none
                        }
                    )
                )
            }
        }
        binding.quickAddPreview.text = parts.joinToString("  ·  ")
        binding.quickAddPreview.isVisible = parts.isNotEmpty()
    }

    companion object {
        const val REQUEST_KEY = "quick_add"
        const val EXTRA_TEXT = "text"
        const val EXTRA_OPEN_EDITOR = "open_editor"
        const val TAG = "frag_tag_quick_add"
    }
}
