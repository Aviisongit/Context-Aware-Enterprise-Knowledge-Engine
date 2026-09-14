import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''
const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL || 'http://localhost:8080'

async function apiRequest(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      Accept: 'application/json',
      ...(!options.body || options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...options.headers,
    },
    ...options,
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(errorText || 'Request failed')
  }

  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    return response.json()
  }

  return response.text()
}

export async function uploadDocument(file) {
  const formData = new FormData()
  formData.append('file', file)

  return apiRequest('/api/v1/documents', {
    method: 'POST',
    body: formData,
  })
}

export async function queryKnowledge(question) {
  return apiRequest('/api/v1/query', {
    method: 'POST',
    body: JSON.stringify({ question }),
  })
}

export async function fetchJob(jobId) {
  return apiRequest(`/api/v1/jobs/${jobId}`)
}

export function subscribeToJob(jobId, onMessage) {
  const client = new Client({
    webSocketFactory: () => new SockJS(`${WS_BASE_URL}/ws-knowledge`),
    reconnectDelay: 5000,
    onConnect: () => {
      client.subscribe(`/topic/jobs/${jobId}`, (message) => {
        onMessage(JSON.parse(message.body))
      })
    },
    onStompError: (frame) => {
      console.error('STOMP error', frame)
    },
  })

  client.activate()
  return client
}

export function subscribeToGlobalTelemetry(onMessage) {
  const client = new Client({
    webSocketFactory: () => new SockJS(`${WS_BASE_URL}/ws-knowledge`),
    reconnectDelay: 5000,
    onConnect: () => {
      client.subscribe('/topic/ingestion', (message) => {
        onMessage(JSON.parse(message.body))
      })
    },
    onStompError: (frame) => {
      console.error('STOMP error', frame)
    },
  })

  client.activate()
  return client
}
