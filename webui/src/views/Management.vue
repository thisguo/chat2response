<script setup>
import { ref, onMounted, provide } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '../store/auth'
import { listProjects, getProject, deleteProject } from '../api'
import ProjectEditor from '../components/ProjectEditor.vue'
import NewProjectDialog from '../components/NewProjectDialog.vue'

const router = useRouter()
const projects = ref([])
const selectedId = ref(null)
const selectedProject = ref(null)
const showNewDialog = ref(false)
const loading = ref(false)

provide('refreshProjects', loadProjects)

async function loadProjects() {
  loading.value = true
  try {
    projects.value = await listProjects()
    if (selectedId.value) {
      const found = projects.value.find(p => p.id === selectedId.value)
      if (!found) {
        selectedId.value = null
        selectedProject.value = null
      }
    }
  } catch {
    // 401 已在 api/index.js 统一处理并跳转登录页
  } finally {
    loading.value = false
  }
}

async function selectProject(id) {
  selectedId.value = id
  try {
    selectedProject.value = await getProject(id)
  } catch (e) {
    console.error(e)
  }
}

async function handleDeleteProject(e, id) {
  e.stopPropagation()
  if (!confirm('确定删除此项目?')) return
  try {
    await deleteProject(id)
    if (selectedId.value === id) {
      selectedId.value = null
      selectedProject.value = null
    }
    loadProjects()
  } catch (err) {
    alert(err.message)
  }
}

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}

onMounted(loadProjects)
</script>

<template>
  <div class="management">
    <div class="sidebar">
      <div class="sidebar-header">
        <span class="sidebar-title">Chat2Response</span>
        <button class="icon-btn add-btn" @click="showNewDialog = true" title="新增项目">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
            <path d="M8 1v14M1 8h14" stroke="currentColor" stroke-width="1.5" fill="none"/>
          </svg>
        </button>
      </div>
      <div class="project-list">
        <div
          v-for="p in projects"
          :key="p.id"
          class="project-item"
          :class="{ active: selectedId === p.id }"
          @click="selectProject(p.id)"
        >
          <div class="project-info">
            <span class="project-name" :title="p.sourceModel">{{ p.sourceModel }}</span>
            <span class="project-target" :title="p.targetModel">{{ p.targetModel }}</span>
          </div>
          <button class="icon-btn del-btn" @click="handleDeleteProject($event, p.id)" title="删除项目">
            <svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor">
              <path d="M2 2l12 12M14 2L2 14" stroke="currentColor" stroke-width="1.5" fill="none"/>
            </svg>
          </button>
        </div>
        <div v-if="projects.length === 0 && !loading" class="empty-hint">暂无项目</div>
      </div>
      <div class="sidebar-footer">
        <span class="user-info">{{ auth.username }}</span>
        <button class="icon-btn logout-btn" @click="handleLogout" title="登出">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor">
            <path d="M2 2h7v2H4v8h5v2H2V2zm7 4l4 3-4 3V9h5V7H9V6z"/>
          </svg>
        </button>
      </div>
    </div>
    <div class="main-content">
      <ProjectEditor
        v-if="selectedProject"
        :project="selectedProject"
        @refresh="loadProjects"
        @updated="(p) => selectedProject = p"
      />
      <div v-else class="no-selection">
        <p>选择左侧项目进行编辑</p>
      </div>
    </div>
    <NewProjectDialog
      v-if="showNewDialog"
      @close="showNewDialog = false"
      @created="(id) => { showNewDialog = false; loadProjects(); selectProject(id) }"
    />
  </div>
</template>

<style scoped>
.management {
  display: flex;
  height: 100vh;
  background: #1e1e1e;
  color: #ccc;
}
.sidebar {
  width: 240px;
  min-width: 240px;
  background: #252526;
  border-right: 1px solid #3c3c3c;
  display: flex;
  flex-direction: column;
}
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #3c3c3c;
}
.sidebar-title {
  font-size: 13px;
  font-weight: 600;
  color: #e0e0e0;
}
.project-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}
.project-item {
  display: flex;
  align-items: center;
  padding: 6px 8px 6px 12px;
  cursor: pointer;
  border-left: 2px solid transparent;
}
.project-item:hover {
  background: #2a2d2e;
}
.project-item:hover .del-btn {
  opacity: 1;
}
.project-item.active {
  background: #37373d;
  border-left-color: #007acc;
}
.project-info {
  flex: 1;
  min-width: 0;
}
.project-name {
  display: block;
  font-size: 13px;
  color: #e0e0e0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.project-target {
  display: block;
  font-size: 11px;
  color: #666;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 1px;
}
.del-btn {
  opacity: 0;
  flex-shrink: 0;
  margin-left: 4px;
  color: #f44747;
}
.del-btn:hover {
  background: #5a1d1d;
  color: #f44747;
}
.empty-hint {
  padding: 20px 12px;
  font-size: 12px;
  color: #666;
  text-align: center;
}
.sidebar-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-top: 1px solid #3c3c3c;
  font-size: 12px;
  color: #888;
}
.icon-btn {
  background: none;
  border: none;
  color: #aaa;
  cursor: pointer;
  padding: 4px;
  border-radius: 3px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.icon-btn:hover {
  background: #3c3c3c;
  color: #e0e0e0;
}
.main-content {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.no-selection {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #555;
  font-size: 14px;
}
</style>
