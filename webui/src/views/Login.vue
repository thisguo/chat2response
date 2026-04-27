<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '../store/auth'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleLogin() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    router.push('/')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-title">Chat2Response</h1>
      <p class="login-subtitle">管理后台</p>
      <form @submit.prevent="handleLogin">
        <div class="form-field">
          <label>用户名</label>
          <input v-model="username" type="text" placeholder="admin" autocomplete="username" />
        </div>
        <div class="form-field">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="请输入密码" autocomplete="current-password" />
        </div>
        <p v-if="error" class="error-msg">{{ error }}</p>
        <button type="submit" :disabled="loading">{{ loading ? '登录中...' : '登录' }}</button>
      </form>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100vh;
  background: #1e1e1e;
}
.login-card {
  background: #252526;
  border: 1px solid #3c3c3c;
  border-radius: 6px;
  padding: 40px 36px;
  width: 340px;
}
.login-title {
  font-size: 22px;
  font-weight: 600;
  color: #e0e0e0;
  margin: 0 0 4px;
  text-align: center;
}
.login-subtitle {
  font-size: 13px;
  color: #888;
  margin: 0 0 28px;
  text-align: center;
}
.form-field {
  margin-bottom: 16px;
}
.form-field label {
  display: block;
  font-size: 12px;
  color: #aaa;
  margin-bottom: 6px;
}
.form-field input {
  width: 100%;
  padding: 8px 10px;
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
  margin: 0 0 12px;
}
button {
  width: 100%;
  padding: 8px;
  background: #007acc;
  color: #fff;
  border: none;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
}
button:hover {
  background: #0098ff;
}
button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
