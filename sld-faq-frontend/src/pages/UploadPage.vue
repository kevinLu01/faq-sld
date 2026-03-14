<template>
  <div class="upload-page">
    <!-- Nav bar -->
    <van-nav-bar
      title="上传资料"
      left-arrow
      @click-left="router.back()"
      fixed
      placeholder
    />

    <div class="page-content">
      <!-- Tab switch: file upload / paste text -->
      <van-tabs v-model:active="activeTab" class="upload-tabs">
        <van-tab title="文件上传" name="file">
          <div class="tab-content">
            <div class="uploader-area">
              <van-uploader
                v-model="fileList"
                :max-count="1"
                :max-size="50 * 1024 * 1024"
                :before-read="beforeRead"
                @oversize="onOversize"
                :after-read="onFileRead"
                result-type="file"
              >
                <div class="upload-trigger">
                  <van-icon name="cloud-upload-o" size="48" color="#1989fa" />
                  <p class="upload-hint">点击选择文件</p>
                  <p class="upload-formats">支持 PDF / DOCX / XLSX / TXT / CSV</p>
                  <p class="upload-limit">最大 50MB</p>
                </div>
              </van-uploader>
            </div>

            <!-- File info after selection -->
            <div v-if="selectedFile" class="file-info-card">
              <van-icon name="description" size="24" color="#1989fa" />
              <div class="file-info-text">
                <p class="file-name">{{ selectedFile.name }}</p>
                <p class="file-size">{{ formatFileSize(selectedFile.size) }}</p>
              </div>
              <van-icon name="cross" size="18" color="#c8c9cc" @click="clearFile" />
            </div>

            <!-- Upload progress -->
            <div v-if="uploading" class="progress-area">
              <p class="progress-label">上传中...</p>
              <van-progress :percentage="uploadProgress" color="#1989fa" />
            </div>

            <!-- Upload button -->
            <van-button
              v-if="selectedFile && !uploadedFile && !uploading"
              type="primary"
              block
              round
              @click="doUpload"
            >
              开始上传
            </van-button>
          </div>
        </van-tab>

        <van-tab title="粘贴文本" name="text">
          <div class="tab-content">
            <van-field
              v-model="pasteText"
              type="textarea"
              placeholder="请粘贴文本内容（支持聊天记录、文档摘录等）"
              rows="10"
              autosize
              show-word-limit
              maxlength="50000"
              class="paste-field"
            />
            <van-button
              type="primary"
              block
              round
              :disabled="!pasteText.trim()"
              :loading="uploading"
              @click="doUploadText"
            >
              上传文本
            </van-button>
          </div>
        </van-tab>
      </van-tabs>

      <!-- Upload success & generate FAQ -->
      <div v-if="uploadedFile" class="success-card">
        <van-icon name="checked" color="#07c160" size="24" />
        <div class="success-info">
          <p class="success-title">上传成功</p>
          <p class="success-file">{{ uploadedFile.originalName }}</p>
        </div>
      </div>

      <div v-if="uploadedFile && !taskId && !taskFinished" class="generate-area">
        <van-button
          type="primary"
          block
          round
          :loading="generating"
          @click="generateFaq"
        >
          生成 FAQ
        </van-button>
        <p class="generate-tip">点击后将自动分析文档内容并提取 FAQ 候选</p>
      </div>

      <!-- Task progress -->
      <div v-if="taskId && !taskFinished" class="task-progress-card">
        <div class="task-header">
          <van-loading size="18" color="#1989fa" />
          <span class="task-status-text">{{ taskStatusText }}</span>
        </div>
        <van-progress :percentage="taskProgress" color="#1989fa" style="margin-top: 12px" />
        <p class="task-progress-label">{{ taskProgress }}%</p>
      </div>

      <!-- Task failed -->
      <div v-if="taskFailed" class="task-error-card">
        <van-icon name="warning-o" color="#ee0a24" size="20" />
        <span class="task-error-text">{{ taskErrorMsg || 'FAQ 生成失败，请重试' }}</span>
        <van-button type="danger" size="small" plain @click="generateFaq">重试</van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import type { UploaderFileListItem } from 'vant'
import { fileApi } from '@/api/file'
import type { FileVO } from '@/types'

const router = useRouter()

const activeTab = ref('file')
const fileList = ref<UploaderFileListItem[]>([])
const selectedFile = ref<File | null>(null)
const pasteText = ref('')

const uploading = ref(false)
const uploadProgress = ref(0)
const uploadedFile = ref<FileVO | null>(null)

const generating = ref(false)
const taskId = ref<number | null>(null)
const taskProgress = ref(0)
const taskFinished = ref(false)
const taskFailed = ref(false)
const taskErrorMsg = ref('')
const taskStatusText = ref('正在处理...')

let pollTimer: ReturnType<typeof setInterval> | null = null

const ALLOWED_EXTS = ['.pdf', '.docx', '.xlsx', '.txt', '.csv']

function beforeRead(file: File | File[]): boolean {
  const f = Array.isArray(file) ? file[0] : file
  const ext = '.' + f.name.split('.').pop()?.toLowerCase()
  if (!ALLOWED_EXTS.includes(ext)) {
    showToast('不支持该文件类型，请上传 PDF / DOCX / XLSX / TXT / CSV')
    return false
  }
  return true
}

function onOversize() {
  showToast('文件大小不能超过 50MB')
}

function onFileRead(item: UploaderFileListItem | UploaderFileListItem[]) {
  const fileItem = Array.isArray(item) ? item[0] : item
  if (fileItem.file) {
    selectedFile.value = fileItem.file
  }
}

function clearFile() {
  selectedFile.value = null
  fileList.value = []
  uploadedFile.value = null
  taskId.value = null
  taskFinished.value = false
  taskFailed.value = false
  uploadProgress.value = 0
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

async function doUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  uploadProgress.value = 0

  const formData = new FormData()
  formData.append('file', selectedFile.value)

  try {
    // Simulate progress
    const progressTimer = setInterval(() => {
      if (uploadProgress.value < 90) uploadProgress.value += 10
    }, 200)

    const res = await fileApi.upload(formData)
    clearInterval(progressTimer)
    uploadProgress.value = 100
    uploadedFile.value = res
    showToast({ message: '上传成功', type: 'success' })
  } catch {
    showToast('上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

async function doUploadText() {
  if (!pasteText.value.trim()) return
  uploading.value = true

  const blob = new Blob([pasteText.value], { type: 'text/plain' })
  const filename = `paste_${Date.now()}.txt`
  const formData = new FormData()
  formData.append('file', blob, filename)

  try {
    const res = await fileApi.upload(formData)
    uploadedFile.value = res
    showToast({ message: '上传成功', type: 'success' })
  } catch {
    showToast('上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

async function generateFaq() {
  if (!uploadedFile.value) return
  generating.value = true
  taskFailed.value = false
  taskErrorMsg.value = ''

  try {
    const res = await fileApi.generateFaq(uploadedFile.value.id)
    taskId.value = res.taskId
    taskProgress.value = 0
    taskStatusText.value = '正在分析文档...'
    pollTaskStatus(res.taskId)
  } catch {
    showToast('启动 FAQ 生成失败')
  } finally {
    generating.value = false
  }
}

function pollTaskStatus(id: number) {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    try {
      const status = await fileApi.getTaskStatus(id)
      taskProgress.value = status.progress

      if (status.status === 'RUNNING') {
        taskStatusText.value = `正在处理... ${status.progress}%`
      } else if (status.status === 'SUCCESS') {
        clearInterval(pollTimer!)
        pollTimer = null
        taskFinished.value = true
        showToast({ message: 'FAQ 生成完成！', type: 'success' })
        setTimeout(() => {
          router.push('/review/list')
        }, 1500)
      } else if (status.status === 'FAILED') {
        clearInterval(pollTimer!)
        pollTimer = null
        taskFailed.value = true
        taskErrorMsg.value = status.errorMsg || 'FAQ 生成失败'
        showToast('FAQ 生成失败')
      }
    } catch {
      // ignore poll errors
    }
  }, 3000)
}

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.upload-page {
  min-height: 100vh;
  background: #f5f5f5;
}

.page-content {
  padding: 12px 16px 24px;
}

.upload-tabs {
  background: white;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 12px;
}

.tab-content {
  padding: 16px;
}

.uploader-area {
  display: flex;
  justify-content: center;
  margin-bottom: 16px;
}

.upload-trigger {
  width: 280px;
  height: 160px;
  border: 2px dashed #c8c9cc;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  background: #fafafa;
}

.upload-hint {
  margin: 0;
  font-size: 15px;
  color: #323233;
  font-weight: 500;
}

.upload-formats {
  margin: 0;
  font-size: 12px;
  color: #969799;
}

.upload-limit {
  margin: 0;
  font-size: 11px;
  color: #c8c9cc;
}

.file-info-card {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #f7f8fa;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 16px;
}

.file-info-text {
  flex: 1;
  min-width: 0;
}

.file-name {
  margin: 0 0 2px;
  font-size: 14px;
  color: #323233;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  margin: 0;
  font-size: 12px;
  color: #969799;
}

.progress-area {
  margin-bottom: 16px;
}

.progress-label {
  margin: 0 0 8px;
  font-size: 13px;
  color: #646566;
}

.paste-field {
  background: #f7f8fa;
  border-radius: 8px;
  margin-bottom: 16px;
}

.success-card {
  background: #f0faf4;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  border: 1px solid #b7ebc4;
}

.success-info {
  flex: 1;
}

.success-title {
  margin: 0 0 2px;
  font-size: 14px;
  font-weight: 600;
  color: #07c160;
}

.success-file {
  margin: 0;
  font-size: 12px;
  color: #646566;
}

.generate-area {
  margin-bottom: 12px;
}

.generate-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: #969799;
  text-align: center;
}

.task-progress-card {
  background: white;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  margin-bottom: 12px;
}

.task-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-status-text {
  font-size: 14px;
  color: #646566;
}

.task-progress-label {
  margin: 6px 0 0;
  text-align: right;
  font-size: 12px;
  color: #969799;
}

.task-error-card {
  background: #fff2f0;
  border-radius: 8px;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #ffccc7;
}

.task-error-text {
  flex: 1;
  font-size: 13px;
  color: #ee0a24;
}
</style>
