import React, { useState, useEffect, useRef } from 'react'
import { useAuth } from '../contexts/AuthContext'
import mqtt from 'mqtt'
import ReactECharts from 'echarts-for-react'

interface Message {
  topicName: string
  payload: string
  protocolType: string
  success: boolean
  createdAt: string
}

interface MetricOverview {
  totalMessages: number
  successCount: number
  failureCount: number
  successRate: number
  avgLatencyMs: number
}

const Dashboard: React.FC = () => {
  const { user, logout, token } = useAuth()
  const [protocol, setProtocol] = useState('TCP')
  const [isConnected, setIsConnected] = useState(false)
  const [messages, setMessages] = useState<Message[]>([])
  const [metrics, setMetrics] = useState<MetricOverview | null>(null)
  const [topics, setTopics] = useState<any[]>([])
  const [selectedTopic, setSelectedTopic] = useState('')
  const [messagePayload, setMessagePayload] = useState('')
  const [qos, setQos] = useState(1)
  const mqttClientRef = useRef<mqtt.MqttClient | null>(null)

  useEffect(() => {
    loadTopics()
    loadMetrics()
    loadRecentMessages()
  }, [])

  const loadTopics = async () => {
    try {
      const response = await fetch('/api/topics', {
        headers: { 'Authorization': `Bearer ${token}` }
      })
      const result = await response.json()
      if (result.success) {
        setTopics(result.data)
      }
    } catch (err) {
      console.error('加载主题失败:', err)
    }
  }

  const loadMetrics = async () => {
    try {
      const response = await fetch('/api/metrics/overview', {
        headers: { 'Authorization': `Bearer ${token}` }
      })
      const result = await response.json()
      if (result.success) {
        setMetrics(result.data)
      }
    } catch (err) {
      console.error('加载指标失败:', err)
    }
  }

  const loadRecentMessages = async () => {
    try {
      const response = await fetch('/api/metrics/messages?limit=20', {
        headers: { 'Authorization': `Bearer ${token}` }
      })
      const result = await response.json()
      if (result.success) {
        setMessages(result.data)
      }
    } catch (err) {
      console.error('加载消息失败:', err)
    }
  }

  const connectMqtt = async (selectedProtocol: string) => {
    try {
      const response = await fetch(`/api/mqtt/connect?protocol=${selectedProtocol}`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      })
      const result = await response.json()
      if (result.success) {
        setProtocol(selectedProtocol)
        setIsConnected(true)
        alert('MQTT连接成功！')
      } else {
        alert('连接失败: ' + result.message)
      }
    } catch (err) {
      alert('连接失败')
    }
  }

  const disconnectMqtt = async () => {
    try {
      await fetch('/api/mqtt/disconnect', {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      })
      setIsConnected(false)
      alert('已断开连接')
    } catch (err) {
      console.error('断开连接失败:', err)
    }
  }

  const publishMessage = async () => {
    if (!selectedTopic || !messagePayload) {
      alert('请选择主题并输入消息内容')
      return
    }
    try {
      const params = new URLSearchParams({
        topic: selectedTopic,
        payload: messagePayload,
        qos: qos.toString(),
        sourceDomain: user?.currentDomain || 'domain-1'
      })
      const response = await fetch(`/api/mqtt/publish?${params}`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` }
      })
      const result = await response.json()
      if (result.success) {
        alert('消息发布成功！')
        setMessagePayload('')
        loadRecentMessages()
        loadMetrics()
      } else {
        alert('发布失败: ' + result.message)
      }
    } catch (err) {
      alert('发布失败')
    }
  }

  const trafficChartOption = {
    title: { text: '消息流量' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['1min', '2min', '3min', '4min', '5min'] },
    yAxis: { type: 'value' },
    series: [{ data: [120, 200, 150, 80, 70], type: 'line', smooth: true }]
  }

  const latencyChartOption = {
    title: { text: '消息延迟' },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['1min', '2min', '3min', '4min', '5min'] },
    yAxis: { type: 'value', name: 'ms' },
    series: [{ data: [50, 75, 60, 45, 80], type: 'bar' }]
  }

  const successRateChartOption = {
    title: { text: '成功率' },
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: '70%',
      data: [
        { value: metrics?.successCount || 0, name: '成功' },
        { value: metrics?.failureCount || 0, name: '失败' }
      ]
    }]
  }

  const domainFlowOption = {
    title: { text: '跨域数据流转拓扑' },
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [{
      type: 'sankey',
      data: [
        { name: '域1' },
        { name: '域2' },
        { name: '域3' },
        { name: '域4' },
        { name: '跨域主题' }
      ],
      links: [
        { source: '域1', target: '跨域主题', value: 100 },
        { source: '域2', target: '跨域主题', value: 80 },
        { source: '跨域主题', target: '域3', value: 60 },
        { source: '跨域主题', target: '域4', value: 120 }
      ],
      emphasis: { focus: 'adjacency' },
      lineStyle: { color: 'gradient', curveness: 0.5 }
    }]
  }

  return (
    <div className="min-h-screen bg-gray-100">
      <nav className="bg-white shadow">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16 items-center">
            <h1 className="text-xl font-bold">跨域数据交换系统</h1>
            <div className="flex items-center gap-4">
              <span className="text-gray-600">当前域: {user?.currentDomain}</span>
              <span className="text-gray-600">用户: {user?.username}</span>
              <button
                onClick={logout}
                className="bg-red-500 text-white px-4 py-2 rounded hover:bg-red-600"
              >
                退出登录
              </button>
            </div>
          </div>
        </div>
      </nav>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="bg-white rounded-lg shadow p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">MQTT连接控制</h2>
          <div className="flex gap-4 items-center">
            <span className={`px-3 py-1 rounded ${isConnected ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
              {isConnected ? '已连接' : '未连接'} ({protocol})
            </span>
            <button
              onClick={() => connectMqtt('TCP')}
              className="bg-blue-500 text-white px-4 py-2 rounded hover:bg-blue-600"
            >
              连接TCP
            </button>
            <button
              onClick={() => connectMqtt('TLS')}
              className="bg-purple-500 text-white px-4 py-2 rounded hover:bg-purple-600"
            >
              连接TLS
            </button>
            <button
              onClick={disconnectMqtt}
              className="bg-gray-500 text-white px-4 py-2 rounded hover:bg-gray-600"
            >
              断开连接
            </button>
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-6">
          <div className="bg-white rounded-lg shadow p-6">
            <h3 className="text-gray-500 text-sm">总消息数</h3>
            <p className="text-3xl font-bold">{metrics?.totalMessages || 0}</p>
          </div>
          <div className="bg-white rounded-lg shadow p-6">
            <h3 className="text-gray-500 text-sm">成功数</h3>
            <p className="text-3xl font-bold text-green-600">{metrics?.successCount || 0}</p>
          </div>
          <div className="bg-white rounded-lg shadow p-6">
            <h3 className="text-gray-500 text-sm">成功率</h3>
            <p className="text-3xl font-bold text-blue-600">{metrics?.successRate?.toFixed(2) || 0}%</p>
          </div>
          <div className="bg-white rounded-lg shadow p-6">
            <h3 className="text-gray-500 text-sm">平均延迟</h3>
            <p className="text-3xl font-bold text-orange-600">{metrics?.avgLatencyMs?.toFixed(2) || 0}ms</p>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
          <div className="bg-white rounded-lg shadow p-6">
            <ReactECharts option={trafficChartOption} style={{ height: '300px' }} />
          </div>
          <div className="bg-white rounded-lg shadow p-6">
            <ReactECharts option={latencyChartOption} style={{ height: '300px' }} />
          </div>
          <div className="bg-white rounded-lg shadow p-6">
            <ReactECharts option={successRateChartOption} style={{ height: '300px' }} />
          </div>
          <div className="bg-white rounded-lg shadow p-6">
            <ReactECharts option={domainFlowOption} style={{ height: '300px' }} />
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold mb-4">发布消息</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">选择主题</label>
                <select
                  value={selectedTopic}
                  onChange={(e) => setSelectedTopic(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg"
                >
                  <option value="">请选择主题</option>
                  {topics.map((topic) => (
                    <option key={topic.id} value={topic.topicName}>
                      {topic.topicName} ({topic.sourceDomain})
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">QoS</label>
                <select
                  value={qos}
                  onChange={(e) => setQos(Number(e.target.value))}
                  className="w-full px-3 py-2 border rounded-lg"
                >
                  <option value={0}>0 - 最多一次</option>
                  <option value={1}>1 - 至少一次</option>
                  <option value={2}>2 - 精确一次</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">消息内容</label>
                <textarea
                  value={messagePayload}
                  onChange={(e) => setMessagePayload(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg"
                  rows={4}
                  placeholder="请输入消息内容"
                />
              </div>
              <button
                onClick={publishMessage}
                className="w-full bg-green-500 text-white py-2 px-4 rounded-lg hover:bg-green-600"
              >
                发布消息
              </button>
            </div>
          </div>

          <div className="bg-white rounded-lg shadow p-6">
            <h2 className="text-lg font-semibold mb-4">最近消息</h2>
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {messages.map((msg, index) => (
                <div key={index} className="border rounded p-3">
                  <div className="flex justify-between text-sm">
                    <span className="font-medium">{msg.topicName}</span>
                    <span className={`px-2 py-1 rounded text-xs ${msg.success ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                      {msg.success ? '成功' : '失败'}
                    </span>
                  </div>
                  <div className="text-sm text-gray-600 mt-1">{msg.payload}</div>
                  <div className="text-xs text-gray-400 mt-1">
                    {msg.protocolType} | {new Date(msg.createdAt).toLocaleString()}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

export default Dashboard
