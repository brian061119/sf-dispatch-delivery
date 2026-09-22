import { App as AntdApp, ConfigProvider } from 'antd';
import 'antd/dist/reset.css';
import 'leaflet/dist/leaflet.css';
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
    <ConfigProvider theme={theme}>
      <AntdApp>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </AntdApp>
    </ConfigProvider>
  </React.StrictMode>);
