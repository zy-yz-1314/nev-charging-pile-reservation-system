<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { aiChatApi } from '@/api/ai'
import { grabPileApi } from '@/api/reservation'

const query = ref('')
const loading = ref(false)
const messages = ref([])
const chatRef = ref(null)

const scrollDown = async () => {
  await nextTick()
  if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
}

const send = async () => {
  const text = query.value.trim()
  if (!text) return
  query.value = ''

  messages.value.push({ role: 'user', content: text, ts: Date.now() })
  await scrollDown()

  loading.value = true
  let lat = null, lng = null
  try {
    if (navigator.geolocation) {
      const pos = await new Promise((resolve, reject) =>
        navigator.geolocation.getCurrentPosition(resolve, reject,
          { enableHighAccuracy: true, timeout: 6000 }))
      lat = Number(pos.coords.latitude.toFixed(6))
      lng = Number(pos.coords.longitude.toFixed(6))
    }
  } catch (_) { /* 定位失败不阻塞 */ }

  try {
    const { data } = await aiChatApi({
      sessionId: 's-' + Date.now(),
      query: text,
      lng, lat,
    })
    messages.value.push({
      role: 'assistant',
      content: data.reply || '未获取回复',
      piles: data.piles || [],
      degraded: data.degraded,
      ts: Date.now(),
    })
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '请求失败,请稍后重试', piles: [], degraded: true, ts: Date.now() })
  } finally { loading.value = false; await scrollDown() }
}

const grab = async (pile) => {
  try { await grabPileApi(pile.pileId); ElMessage.success('抢桩成功!') }
  catch (e) { if (e.message?.includes('等待队列')) ElMessage.warning(e.message) }
}
</script>

<template>
  <div class="ai-page">
    <div class="head pq-fade-up">
      <h1 class="title">🤖 AI 智能<span class="pq-gradient-text">助手</span></h1>
      <p class="subtitle">口语化描述你的充电需求,AI 为你匹配最优方案 (DeepSeek)</p>
    </div>

    <div class="chat-box pq-glass" ref="chatRef">
      <!-- 空态引导 -->
      <div v-if="!messages.length && !loading" class="empty-hint">
        <p>💬 试着告诉我:</p>
        <ul>
          <li>"我在国贸,着急,现在去哪最快充上"</li>
          <li>"帮我找个最近的快充桩,不急,便宜点"</li>
          <li>"附近 5 公里内有没有空闲的慢充桩"</li>
        </ul>
      </div>

      <div v-for="(m, i) in messages" :key="i" class="msg-row" :class="m.role">
        <div class="msg-card" :class="m.role">
          <div class="msg-text">{{ m.content }}</div>
          <!-- AI 推荐桩卡片 -->
          <div v-if="m.piles && m.piles.length" class="pile-strip">
            <div v-for="p in m.piles" :key="p.pileId" class="pchip pq-glass" @click="grab(p)">
              <div class="pchip-top">
                <b>{{ p.stationName }} · {{ p.pileNo }}</b>
                <span :class="p.type === 'FAST' ? 'fast' : 'slow'">{{ p.type === 'FAST' ? '快' : '慢' }}</span>
              </div>
              <div class="pchip-info">
                {{ Number(p.distanceKm).toFixed(1) }}km · {{ p.waitMin }}min · ¥{{ Number(p.finalPrice).toFixed(2) }}
              </div>
              <div class="pchip-score">得分 {{ Number(p.score).toFixed(0) }}</div>
            </div>
          </div>
          <!-- 降级标识 -->
          <div v-if="m.degraded" class="degraded-tag">
            ⚠️ AI 暂时不可用,已自动切换为智能匹配结果
          </div>
        </div>
      </div>

      <!-- 加载 -->
      <div v-if="loading" class="msg-row assistant">
        <div class="msg-card assistant typing">AI 正在分析...
          <span class="dots"><i>.</i><i>.</i><i>.</i></span>
        </div>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-bar pq-glass">
      <el-input
        v-model="query"
        placeholder="例如: 我在国贸,着急,现在去哪最快充上"
        size="large"
        clearable
        @keyup.enter="send"
        :disabled="loading"
      >
        <template #prefix><el-icon><ChatDotRound /></el-icon></template>
      </el-input>
      <el-button type="primary" size="large" :loading="loading" @click="send" class="send-btn">
        <el-icon><Promotion /></el-icon>&nbsp;发送
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.ai-page { display: flex; flex-direction: column; height: calc(100vh - 160px); }
.head { margin-bottom: 20px; flex-shrink: 0; }
.title { font-size: 32px; font-weight: 800; margin: 0 0 6px; }
.subtitle { color: var(--pq-text-dim); margin: 0; }

.chat-box { flex: 1; overflow-y: auto; padding: 24px; margin-bottom: 16px; display: flex; flex-direction: column; gap: 14px; }
.empty-hint { color: var(--pq-text-dim); font-size: 14px; padding: 20px 0; }
.empty-hint ul { margin-top: 10px; padding-left: 20px; }
.empty-hint li { margin-bottom: 6px; color: var(--pq-text-faint); }

.msg-row { display: flex; }
.msg-row.user { justify-content: flex-end; }
.msg-row.assistant { justify-content: flex-start; }
.msg-card {
  max-width: 80%; padding: 14px 18px; border-radius: 16px;
  font-size: 14px; line-height: 1.6;
  animation: pq-fade-up 0.35s var(--pq-ease);
}
.msg-card.user { background: linear-gradient(135deg, var(--pq-primary), var(--pq-primary-2)); color: #042018; font-weight: 600; }
.msg-card.assistant { background: var(--pq-surface-2); border: 1px solid var(--pq-border); }

.typing .dots i { animation: pq-blink 1s infinite; margin: 0 1px; font-style: normal; }
.typing .dots i:nth-child(2) { animation-delay: 0.2s; }
.typing .dots i:nth-child(3) { animation-delay: 0.4s; }
@keyframes pq-blink { 0%,100% { opacity: 0.3; } 50% { opacity: 1; } }

.pile-strip { display: flex; gap: 10px; margin-top: 12px; flex-wrap: wrap; }
.pchip {
  padding: 10px 12px; cursor: pointer; min-width: 150px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.pchip:hover { transform: translateY(-2px); box-shadow: var(--pq-glow); }
.pchip-top { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.pchip-top b { font-size: 13px; }
.pchip-top span { font-size: 10px; padding: 1px 6px; border-radius: 999px; font-weight: 700; }
.pchip-top span.fast { color: var(--pq-charging); background: rgba(42,214,255,0.14); }
.pchip-top span.slow { color: var(--pq-text-dim); background: var(--pq-surface-2); }
.pchip-info { font-size: 11px; color: var(--pq-text-dim); margin-bottom: 2px; }
.pchip-score { font-size: 12px; font-weight: 700; color: var(--pq-primary); }

.degraded-tag { margin-top: 8px; font-size: 11px; padding: 4px 10px; border-radius: 999px; background: rgba(255,176,46,0.15); color: var(--pq-warning); display: inline-block; }

.input-bar { display: flex; gap: 10px; padding: 16px 20px; flex-shrink: 0; align-items: center; }
.send-btn { font-weight: 700; }
</style>
