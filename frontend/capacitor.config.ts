import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.veloxtube.app',
  appName: 'VeloxTube',
  webDir: 'dist',
  backgroundColor: '#0F0F0F',
  server: {
    androidScheme: 'https',
    cleartext: true,
  },
  android: {
    backgroundColor: '#0F0F0F',
    allowMixedContent: true,
    webContentsDebuggingEnabled: false,
  },
  plugins: {
    StatusBar: {
      overlaysWebView: false,
      style: 'DARK',
      backgroundColor: '#0F0F0F',
    },
  },
};

export default config;
