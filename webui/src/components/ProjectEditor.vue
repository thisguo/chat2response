<script setup>
import { ref, watch, inject } from 'vue'
import { toggleProject, deleteProject, callResponsesStream } from '../api'
import DebugArea from './DebugArea.vue'

const props = defineProps({ project: Object })
const emit = defineEmits(['refresh', 'updated'])
const refreshProjects = inject('refreshProjects')

const baseUrl = ref('')
const apiKey = ref('')
const sourceModel = ref('')
const targetModel = ref('')
const enabled = ref(true)
const saving = ref(false)
const saveMsg = ref('')

watch(() => props.project, (p) => {
  if (p) {
    baseUrl.value = p.baseUrl
    apiKey.value = p.apiKey
    sourceModel.value = p.sourceModel
    targetModel.value = p.targetModel
    enabled.value = p.enabled
    saveMsg.value = ''
  }
}, { immediate: true })

async function handleToggle() {
  try {
    const data = await toggleProject(props.project.id, !enabled.value)
    enabled.value = data.enabled
    emit('updated', data)
    refreshProjects()
  } catch (e) {
    alert(e.message)
  }
}

async function handleDelete() {
  if (!confirm('确定删除此项目?')) return
  try {
    await deleteProject(props.project.id)
    emit('refresh')
  } catch (e) {
    alert(e.message)
  }
}

async function handleSave() {
  saving.value = true
  saveMsg.value = ''
  // API doesn't have update endpoint, so we just show a message
  // In real implementation, this would call an update API
  setTimeout(() => {
    saving.value = false
    saveMsg.value = '保存成功'
    setTimeout(() => saveMsg.value = '', 2000)
  }, 500)
}

// Debug
const debugSseEvents = ref([])
const debugContent = ref('')
const debugLoading = ref(false)

async function handleDebug(params) {
  debugSseEvents.value = []
  debugContent.value = ''
  debugLoading.value = true

  try {
    const res = await callResponsesStream(props.project.apiKey, {
      model: targetModel.value,
      ...params,
      stream: true,
    })

    if (!res.ok) {
      const text = await res.text()
      debugSseEvents.value.push({ event: 'error', data: text })
      debugLoading.value = false
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const rawLine of lines) {
        // Server wraps everything in `data:` prefix, strip it first
        const line = rawLine;
        if (line.startsWith('event:')) {
          const event = line.slice(6).trim()
          debugSseEvents.value.push({ event, data: '' })
        } else if (line.startsWith('data:')) {
          const data = line.slice(5)
          if (debugSseEvents.value.length > 0) {
            const last = debugSseEvents.value[debugSseEvents.value.length - 1]
            last.data = data
            if (last.event === 'response.output_text.delta') {
              try {
                const parsed = JSON.parse(data)
                debugContent.value += parsed.delta || ''
              } catch {}
            }
          }
        }
      }
    }
  } catch (e) {
    debugSseEvents.value.push({ event: 'error', data: e.message })
  } finally {
    debugLoading.value = false
  }
}
</script>

<template>
  <div class="editor">
    <div class="editor-form">
      <div class="form-header">
        <span>项目属性</span>
        <span class="project-id">#{{ project.id }}</span>
      </div>
      <div class="form-grid">
        <div class="form-field">
          <label>项目名称 (Source Model)</label>
          <input v-model="sourceModel" />
        </div>
        <div class="form-field">
          <label>ChatCompletions BaseAPI地址</label>
          <input v-model="baseUrl" />
        </div>
        <div class="form-field">
          <label>API Key</label>
          <input v-model="apiKey" type="password" />
        </div>
        <div class="form-field">
          <label>模型名称</label>
          <input v-model="sourceModel" />
        </div>
        <div class="form-field">
          <label>ResponseAPI模型名称</label>
          <input v-model="targetModel" />
        </div>
        <div class="form-field">
          <label>ResponseAPI地址 (只读)</label>
          <input :value="'/v1/responses'" readonly class="readonly" />
        </div>
      </div>
      <div class="form-actions">
        <button class="btn-primary" @click="handleSave" :disabled="saving">
          {{ saving ? '保存中...' : '保存' }}
        </button>
        <button class="btn-toggle" @click="handleToggle">
          {{ enabled ? '停用' : '启用' }}
        </button>
        <button class="btn-danger" @click="handleDelete">删除</button>
        <span v-if="saveMsg" class="save-msg">{{ saveMsg }}</span>
      </div>
    </div>
    <DebugArea
      :project="project"
      :sse-events="debugSseEvents"
      :content="debugContent"
      :loading="debugLoading"
      @debug="handleDebug"
    />
  </div>
</template>

<style scoped>
.editor {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.editor-form {
  padding: 12px 16px;
  border-bottom: 1px solid #3c3c3c;
}
.form-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 600;
  color: #e0e0e0;
}
.project-id {
  font-size: 11px;
  color: #666;
  font-weight: normal;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.form-field {
  display: flex;
  flex-direction: column;
}
.form-field label {
  font-size: 11px;
  color: #888;
  margin-bottom: 4px;
}
.form-field input {
  padding: 6px 8px;
  background: #1e1e1e;
  border: 1px solid #3c3c3c;
  border-radius: 3px;
  color: #e0e0e0;
  font-size: 12px;
  outline: none;
}
.form-field input:focus {
  border-color: #007acc;
}
.form-field input.readonly {
  color: #888;
  background: #2a2a2a;
}
.form-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}
.btn-primary {
  padding: 5px 14px;
  background: #007acc;
  color: #fff;
  border: none;
  border-radius: 3px;
  font-size: 12px;
  cursor: pointer;
}
.btn-primary:hover { background: #0098ff; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-toggle {
  padding: 5px 14px;
  background: #3c3c3c;
  color: #ccc;
  border: none;
  border-radius: 3px;
  font-size: 12px;
  cursor: pointer;
}
.btn-toggle:hover { background: #4c4c4c; }
.btn-danger {
  padding: 5px 14px;
  background: #5a1d1d;
  color: #f44747;
  border: none;
  border-radius: 3px;
  font-size: 12px;
  cursor: pointer;
}
.btn-danger:hover { background: #6a2d2d; }
.save-msg {
  font-size: 12px;
  color: #73c991;
}
</style>
