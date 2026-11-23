import {defineConfig, devices} from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 90_000,
  workers: 1,
  use: {
    baseURL: process.env.COMPARE_URL ?? 'http://127.0.0.1:13000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    reducedMotion: 'reduce',
    launchOptions: process.platform === 'linux' ? {args: ['--enable-unsafe-swiftshader', '--use-angle=swiftshader']} : undefined,
  },
  projects: [
    {name: 'desktop', use: {...devices['Desktop Chrome'], viewport: {width: 1280, height: 800}}},
    {name: 'mobile', use: {...devices['Pixel 7'], deviceScaleFactor: 1, defaultBrowserType: 'chromium', viewport: {width: 360, height: 800}}},
  ],
});
