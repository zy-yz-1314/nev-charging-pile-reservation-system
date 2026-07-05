import { useUserStore } from '@/store/user'

/** WebSocket 单例管理器:连接 /ws/charging,支持订阅/消息回调/自动重连/心跳 */
class ChargingWS {
  constructor() {
    this.ws = null
    this.url = null
    this.listeners = []
    this.reconnectTimer = null
    this.pingTimer = null
    this.intendClose = false
  }

  connect() {
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) return
    this.intendClose = false
    const store = useUserStore()
    if (!store.token) return
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    this.url = `${proto}://${location.host}/ws/charging?token=${encodeURIComponent(store.token)}`
    try {
      this.ws = new WebSocket(this.url)
      this.ws.onopen = () => {
        this._startPing()
        this._emit({ type: 'OPEN' })
      }
      this.ws.onmessage = (e) => {
        try {
          const msg = JSON.parse(e.data)
          this._emit(msg)
        } catch (_) { /* ignore */ }
      }
      this.ws.onclose = () => {
        this._stopPing()
        if (!this.intendClose) this._scheduleReconnect()
      }
      this.ws.onerror = () => { /* onclose will follow */ }
    } catch (_) { this._scheduleReconnect() }
  }

  disconnect() {
    this.intendClose = true
    this._stopPing()
    clearTimeout(this.reconnectTimer)
    if (this.ws) {
      this.ws.close(1000)
      this.ws = null
    }
  }

  subscribe(stationIds, pileIds) {
    this._send({ type: 'SUBSCRIBE', stationIds: stationIds || [], pileIds: pileIds || [] })
  }

  on(typeOrFn, fn) {
    if (typeof typeOrFn === 'function') {
      this.listeners.push({ fn: typeOrFn })
      return () => { this.listeners = this.listeners.filter((l) => l.fn !== typeOrFn) }
    }
    const unsubs = []
    /* eslint-disable-next-line no-unused-vars */
    for (const _t of [].concat(typeOrFn)) {
      const wrapped = (msg) => { if (msg.type === _t) fn(msg) }
      this.listeners.push({ type: _t, fn: wrapped })
      unsubs.push(() => { this.listeners = this.listeners.filter((l) => l.fn !== wrapped) })
    }
    return () => unsubs.forEach((u) => u())
  }

  _send(obj) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(obj))
    }
  }

  _emit(msg) {
    for (const l of [...this.listeners]) {
      try { if (!l.type || l.type === msg.type) l.fn(msg) } catch (_) { /* noop */ }
    }
  }

  _startPing() {
    this._stopPing()
    this.pingTimer = setInterval(() => this._send({ type: 'PING' }), 25_000)
  }

  _stopPing() {
    clearInterval(this.pingTimer)
  }

  _scheduleReconnect() {
    clearTimeout(this.reconnectTimer)
    this.reconnectTimer = setTimeout(() => this.connect(), 3000)
  }
}

export const ws = new ChargingWS()
