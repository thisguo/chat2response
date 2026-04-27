import { reactive } from 'vue'
import { isLoggedIn, getMe, login as apiLogin, logout as apiLogout } from '../api'

export const auth = reactive({
  loggedIn: isLoggedIn(),
  username: '',

  async check() {
    if (!isLoggedIn()) {
      this.loggedIn = false
      this.username = ''
      return false
    }
    try {
      const data = await getMe()
      this.username = data.username
      this.loggedIn = true
      return true
    } catch {
      this.loggedIn = false
      this.username = ''
      localStorage.removeItem('login_token')
      return false
    }
  },

  async login(username, password) {
    const data = await apiLogin(username, password)
    this.loggedIn = true
    this.username = data.username
    return data
  },

  async logout() {
    await apiLogout()
    this.loggedIn = false
    this.username = ''
  },
})
