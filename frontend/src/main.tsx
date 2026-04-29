/**
 * 应用入口 —— 使用 React 18 createRoot API 挂载根组件
 *
 * 关键配置：
 * - BrowserRouter 提供 HTML5 History 路由
 * - StrictMode 在开发环境下进行额外检查（双重渲染、过时 API 警告）
 */
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
