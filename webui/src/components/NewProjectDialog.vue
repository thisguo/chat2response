<script setup>
import { ref } from 'vue'
import { createProject } from '../api'

const emit = defineEmits(['close', 'created'])

const baseUrl = ref('https://api.openai.com')
const apiKey = ref('')
const sourceModel = ref('')
const error = ref('')
const loading = ref(false)

async function handleSubmit() {
  error.value = ''
  if (!baseUrl.value || !apiKey.value || !sourceModel.value) {
    error.value = '请填写所有必填项'
    return
  }
  loading.value = true
  try {
    const data = await createProject({
      baseUrl: baseUrl.value,
      apiKey: apiKey.value,
      sourceModel: sourceModel.value,
    })
    emit('created', data.id)
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="dialog-overlay" @click.self="emit('close')">
    <div class="dialog">
      <div class="dialog-header">
        <span>新增转换项目</span>
        <button class="close-btn" @click="emit('close')">
          <svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor">
            <path d="M2 2l12 12M14 2L2 14" stroke="currentColor" stroke-width="1.5" fill="none"/>
          </svg>
        </button>
      </div>
      <form @submit.prevent="handleSubmit" class="dialog-body">
        <div class="form-field">
          <label>BaseURL <span class="required">*</span></label>
          <input v-model="baseUrl" placeholder="https://api.openai.com" />
        </div>
        <div class="form-field">
          <label>API Key <span class="required">*</span></label>
          <input v-model="apiKey" type="password" placeholder="sk-xxxx" />
        </div>
        <div class="form-field">
          <label>模型名称 <span class="required">*</span></label>
          <input v-model="sourceModel" placeholder="gpt-4o-mini" />
        </div>
        <p v-if="error" class="error-msg">{{ error }}</p>
        <div class="dialog-footer">
          <button type="button" class="btn-cancel" @click="emit('close')">取消</button>
          <button type="submit" class="btn-primary" :disabled="loading">
            {{ loading ? '创建中...' : '创建' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
}
.dialog {
  background: #252526;
  border: 1px solid #3c3c3c;
  border-radius: 6px;
  width: 400px;
}
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #3c3c3c;
  font-size: 13px;
  font-weight: 600;
  color: #e0e0e0;
}
.close-btn {
  background: none;
  border: none;
  color: #aaa;
  cursor: pointer;
  padding: 2px;
  border-radius: 3px;
  display: flex;
}
.close-btn:hover {
  background: #3c3c3c;
  color: #e0e0e0;
}
.dialog-body {
  padding: 16px;
}
.form-field {
  margin-bottom: 14px;
}
.form-field label {
  display: block;
  font-size: 12px;
  color: #aaa;
  margin-bottom: 5px;
}
.required {
  color: #f44747;
}
.form-field input {
  width: 100%;
  padding: 7px 10px;
  background: #1e1e1e;
  border: 1px solid #3c3c3c;
  border-radius: 4px;
  color: #e0e0e0;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
}
.form-field input:focus {
  border-color: #007acc;
}
.error-msg {
  color: #f44747;
  font-size: 12px;
  margin: 0 0 10px;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
.btn-cancel {
  padding: 6px 14px;
  background: #3c3c3c;
  color: #ccc;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}
.btn-cancel:hover {
  background: #4c4c4c;
}
.btn-primary {
  padding: 6px 14px;
  background: #007acc;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}
.btn-primary:hover {
  background: #0098ff;
}
.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
