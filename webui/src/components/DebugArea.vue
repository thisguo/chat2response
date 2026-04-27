<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { marked } from 'marked'
import * as monaco from 'monaco-editor'

const props = defineProps({
  project: Object,
  sseEvents: { type: Array, default: () => [] },
  content: { type: String, default: '' },
  loading: Boolean,
})
const emit = defineEmits(['debug'])

const editorRef = ref(null)
const ssePanelRef = ref(null)
const previewMode = ref('render') // 'render' | 'source'
const lastParams = ref(localStorage.getItem('debug_params') || JSON.stringify({
  model: '',
  input: '你好，介绍一下你自己',
  instructions: '你是一个简洁的助手',
  temperature: 0.7,
  stream: true,
}, null, 2))

let editor = null

onMounted(async () => {
  await nextTick()
  if (editorRef.value) {
    editor = monaco.editor.create(editorRef.value, {
      value: lastParams.value,
      language: 'json',
      theme: 'vs-dark',
      minimap: { enabled: false },
      fontSize: 12,
      lineNumbers: 'on',
      scrollBeyondLastLine: false,
      automaticLayout: true,
      tabSize: 2,
    })
  }
})

onBeforeUnmount(() => {
  if (editor) editor.dispose()
})

watch(() => props.sseEvents, () => {
  nextTick(() => {
    if (ssePanelRef.value) {
      ssePanelRef.value.scrollTop = ssePanelRef.value.scrollHeight
    }
  })
}, { deep: true })

function handleDebug() {
  const text = editor ? editor.getValue() : lastParams.value
  lastParams.value = text
  localStorage.setItem('debug_params', text)
  try {
    const params = JSON.parse(text)
    emit('debug', params)
  } catch (e) {
    alert('JSON 格式错误: ' + e.message)
  }
}

function renderMarkdown(text) {
  return marked(text || '')
}
</script>

<template>
  <div class="debug-area">
    <div class="debug-top">
      <div class="debug-header">
        <span>调试入参</span>
        <button class="debug-btn" @click="handleDebug" :disabled="loading">
          {{ loading ? '调试中...' : '发送调试' }}
        </button>
      </div>
      <div ref="editorRef" class="monaco-container"></div>
    </div>
    <div class="debug-bottom">
      <div class="debug-bottom-left">
        <div class="panel-header">SSE 流事件</div>
        <div ref="ssePanelRef" class="sse-panel">
          <div v-for="(ev, i) in sseEvents" :key="i" class="sse-event">
            <span class="sse-event-name">{{ ev.event }}</span>
            <span class="sse-event-data">{{ ev.data }}</span>
          </div>
          <div v-if="sseEvents.length === 0" class="sse-empty">等待调试...</div>
        </div>
      </div>
      <div class="debug-bottom-right">
        <div class="panel-header">
          <span>内容预览</span>
          <div class="preview-tabs">
            <button
              :class="['tab-btn', { active: previewMode === 'render' }]"
              @click="previewMode = 'render'"
            >渲染</button>
            <button
              :class="['tab-btn', { active: previewMode === 'source' }]"
              @click="previewMode = 'source'"
            >源码</button>
          </div>
        </div>
        <div class="preview-panel">
          <div v-if="previewMode === 'render'" class="markdown-body" v-html="renderMarkdown(content)"></div>
          <pre v-else class="source-body">{{ content }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.debug-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
.debug-top {
  flex: 0 0 200px;
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid #3c3c3c;
}
.debug-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #252526;
  font-size: 12px;
  color: #aaa;
}
.debug-btn {
  padding: 3px 10px;
  background: #007acc;
  color: #fff;
  border: none;
  border-radius: 3px;
  font-size: 11px;
  cursor: pointer;
}
.debug-btn:hover { background: #0098ff; }
.debug-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.monaco-container {
  flex: 1;
  min-height: 0;
}
.debug-bottom {
  flex: 1;
  display: flex;
  min-height: 0;
}
.debug-bottom-left {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #3c3c3c;
  min-width: 0;
}
.debug-bottom-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 5px 12px;
  background: #252526;
  font-size: 12px;
  color: #aaa;
  border-bottom: 1px solid #3c3c3c;
}
.preview-tabs {
  display: flex;
  gap: 2px;
}
.tab-btn {
  padding: 2px 8px;
  background: none;
  border: none;
  color: #888;
  font-size: 11px;
  cursor: pointer;
  border-radius: 2px;
}
.tab-btn:hover { color: #ccc; }
.tab-btn.active { color: #e0e0e0; background: #3c3c3c; }
.sse-panel {
  flex: 1;
  overflow-y: auto;
  padding: 6px 12px;
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 11px;
}
.sse-event {
  margin-bottom: 4px;
  line-height: 1.5;
}
.sse-event-name {
  color: #569cd6;
  margin-right: 8px;
}
.sse-event-data {
  color: #ce9178;
  word-break: break-all;
}
.sse-empty {
  color: #555;
  font-size: 12px;
  padding: 20px 0;
  text-align: center;
}
.preview-panel {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
}
.markdown-body {
  color: #ccc;
  font-size: 13px;
  line-height: 1.6;
}
.markdown-body :deep(p) { margin: 0 0 8px; }
.markdown-body :deep(code) {
  background: #2a2a2a;
  padding: 1px 4px;
  border-radius: 2px;
  font-size: 12px;
}
.markdown-body :deep(pre) {
  background: #1e1e1e;
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
}
.source-body {
  color: #ccc;
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
