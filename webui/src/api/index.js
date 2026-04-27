const BASE_URL = ''

function getToken() {
  return localStorage.getItem('login_token') || ''
}

function authHeaders() {
  return { 'Authorization': `Bearer ${getToken()}` }
}

async function request(method, url, body, headers = {}) {
  const opts = {
    method,
    headers: { 'Content-Type': 'application/json', ...headers },
  }
  if (body !== undefined) opts.body = JSON.stringify(body)
  const res = await fetch(BASE_URL + url, opts)
  const text = await res.text()
  let data = null
  if (text) {
    try { data = JSON.parse(text) } catch { data = text }
  }
  if (res.status === 401) {
    localStorage.removeItem('login_token')
    localStorage.removeItem('token_expires')
    window.location.href = '/login'
    throw new Error('未登录或登录已过期')
  }
  if (!res.ok) {
    const msg = data?.error?.message || `HTTP ${res.status}`
    throw new Error(msg)
  }
  return data
}

export async function login(username, password) {
  const data = await request('POST', '/admin/auth/login', { username, password })
  localStorage.setItem('login_token', data.token)
  localStorage.setItem('token_expires', data.expiresAt)
  return data
}

export async function getMe() {
  return request('GET', '/admin/auth/me', undefined, authHeaders())
}

export async function logout() {
  try {
    await request('POST', '/admin/auth/logout', undefined, authHeaders())
  } finally {
    localStorage.removeItem('login_token')
    localStorage.removeItem('token_expires')
  }
}

export function isLoggedIn() {
  const token = localStorage.getItem('login_token')
  const expires = localStorage.getItem('token_expires')
  if (!token) return false
  if (expires && Date.now() / 1000 > Number(expires)) {
    localStorage.removeItem('login_token')
    localStorage.removeItem('token_expires')
    return false
  }
  return true
}

// Projects
export async function listProjects() {
  return request('GET', '/admin/projects', undefined, authHeaders())
}

export async function getProject(id) {
  return request('GET', `/admin/projects/${id}`, undefined, authHeaders())
}

export async function createProject(data) {
  return request('POST', '/admin/projects', data, authHeaders())
}

export async function deleteProject(id) {
  return request('DELETE', `/admin/projects/${id}`, undefined, authHeaders())
}

export async function toggleProject(id, enabled) {
  return request('PATCH', `/admin/projects/${id}/enabled?enabled=${enabled}`, undefined, authHeaders())
}

// Gateway - debug call
export async function callResponses(apiKey, body) {
  const res = await fetch(BASE_URL + '/v1/responses', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${apiKey}`,
    },
    body: JSON.stringify(body),
  })
  return res
}

export async function callResponsesStream(apiKey, body) {
  const res = await fetch(BASE_URL + '/v1/responses', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${apiKey}`,
    },
    body: JSON.stringify({ ...body, stream: true }),
  })
  return res
}
