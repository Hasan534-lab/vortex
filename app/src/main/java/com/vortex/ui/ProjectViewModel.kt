package com.vortex.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vortex.BuildConfig
import com.vortex.data.*
import com.vortex.model.Spec
import com.vortex.model.Specs
import kotlinx.coroutines.launch

/**
 * State of one open project. Three different numbers are kept apart on purpose:
 * logged (written down by the operator from the real mixer), ask (requested by the controller)
 * and applied (an ask the operator accepted, which then becomes the new logged value).
 * Network sync between the two phones comes in stage 3; until then each phone only sees its own data.
 */
class ProjectViewModel(app: Application, private val pid: Long) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).projects()
    val controller = BuildConfig.ROLE == "CONTROLLER"

    data class V(val logged: Double? = null, val ask: Double? = null)

    val vals = mutableStateMapOf<String, V>()
    val messages = mutableStateListOf<MessageEntity>()
    val vocals = mutableStateListOf<VocalLineEntity>()

    init {
        viewModelScope.launch {
            dao.values(pid).forEach { vals[it.controlKey] = V(it.logged, it.ask) }
            messages.addAll(dao.messages(pid))
            var v = dao.vocalLines(pid)
            if (v.isEmpty()) {
                dao.insertVocal(VocalLineEntity(projectId = pid, name = "Vocal", position = 0))
                v = dao.vocalLines(pid)
            }
            vocals.addAll(v)
        }
    }

    /** Value drawn on the control. The controller sees their own request; the operator sees the logged value. */
    fun shown(spec: Spec): Double {
        val v = vals[spec.key]
        return if (controller && v?.ask != null) v.ask else v?.logged ?: spec.def
    }

    /** Marker for a pending request: yellow on the operator phone, white (real value) on the controller phone. */
    fun ghost(spec: Spec): Pair<Double, Boolean>? {
        val v = vals[spec.key] ?: return null
        val a = v.ask ?: return null
        if (a == v.logged) return null
        return if (controller) (v.logged ?: spec.def) to true else a to false
    }

    fun valueText(spec: Spec): Pair<String, Boolean> {
        val v = vals[spec.key]
        val a = v?.ask
        return if (controller && a != null && a != v.logged) "ask ${spec.fmt(a)}" to true else spec.fmt(v?.logged) to false
    }

    /** One press of a + or - button: moves by the control's own step (5 ms for delay time). */
    fun step(spec: Spec, dir: Int) {
        change(spec, shown(spec) + dir * spec.step)
        finish(spec)
    }

    fun change(spec: Spec, raw: Double) {
        val nv = spec.snap(raw)
        val cur = vals[spec.key] ?: V()
        vals[spec.key] = if (controller) cur.copy(ask = nv) else cur.copy(logged = nv)
    }

    /** Called when the finger lifts: save the value, and (controller) send one request message. */
    fun finish(spec: Spec) {
        val cur = vals[spec.key] ?: return
        viewModelScope.launch {
            dao.upsertValue(ValueEntity(pid, spec.key, cur.logged, cur.ask))
            val a = cur.ask
            if (controller && a != null && a != cur.logged) {
                val text = "${spec.title}: ${spec.fmt(cur.logged)} → ${spec.fmt(a)}"
                val i = messages.indexOfFirst { it.controlKey == spec.key && it.status == "PENDING" }
                if (i >= 0) {
                    val m = messages[i].copy(text = text)
                    dao.updateMessage(m); messages[i] = m
                } else {
                    val m0 = MessageEntity(projectId = pid, controlKey = spec.key, section = spec.section,
                        text = text, status = "PENDING", sender = "controller", time = System.currentTimeMillis())
                    messages.add(m0.copy(id = dao.insertMessage(m0)))
                }
            }
        }
    }

    /** Operator confirms the change was really made on the mixer. */
    fun accept(m: MessageEntity) {
        if (controller || m.status != "PENDING") return
        val key = m.controlKey ?: return
        viewModelScope.launch {
            val cur = vals[key] ?: V()
            val nv = V(logged = cur.ask ?: cur.logged, ask = null)
            vals[key] = nv
            dao.upsertValue(ValueEntity(pid, key, nv.logged, null))
            val u = m.copy(status = "APPLIED")
            dao.updateMessage(u)
            val i = messages.indexOfFirst { it.id == m.id }
            if (i >= 0) messages[i] = u
        }
    }

    fun sendText(t: String) {
        val text = t.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val m0 = MessageEntity(projectId = pid, controlKey = null, section = "Text", text = text,
                status = "TEXT", sender = if (controller) "controller" else "operator", time = System.currentTimeMillis())
            messages.add(m0.copy(id = dao.insertMessage(m0)))
        }
    }

    fun addVocal() = viewModelScope.launch {
        val e = VocalLineEntity(projectId = pid, name = "Vocal ${vocals.size + 1}", position = vocals.size)
        vocals.add(e.copy(id = dao.insertVocal(e)))
    }

    fun renameVocal(l: VocalLineEntity, name: String) = viewModelScope.launch {
        val n = name.trim().ifEmpty { return@launch }
        val u = l.copy(name = n)
        dao.updateVocal(u)
        val i = vocals.indexOfFirst { it.id == l.id }
        if (i >= 0) vocals[i] = u
    }

    fun removeVocal(l: VocalLineEntity) = viewModelScope.launch {
        if (vocals.size <= 1) return@launch
        dao.deleteVocal(l.id)
        val keys = listOf("voc.${l.id}") + Specs.eqSuffixes.map { "eq.${l.id}.$it" }
        keys.forEach { dao.deleteValue(pid, it); vals.remove(it) }
        vocals.removeAll { it.id == l.id }
    }

    companion object {
        fun factory(app: Application, pid: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProjectViewModel(app, pid) as T
        }
    }
}
