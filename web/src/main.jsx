import { App as AntdApp, ConfigProvider } from 'antd';
// Some antd components fall back to a Chinese default locale (Pagination
// renders "items per page" in Chinese, Table its sort tooltip, etc.), so pin
// the locale to English explicitly — otherwise Chinese leaks into the UI.
import enUS from 'antd/locale/en_US';
import 'antd/dist/reset.css';
import 'leaflet/dist/leaflet.css';
import './index.css';
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { BRAND_NAME } from './lib/brand';
import { theme } from './theme';
// Browser tab title comes from the single brand constant (index.html only
// carries a static fallback for pre-JS rendering).
document.title = BRAND_NAME;
ReactDOM.createRoot(document.getElementById('root')).render(<React.StrictMode>
    <ConfigProvider theme={theme} locale={enUS}>
      <AntdApp>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  </React.StrictMode>);
