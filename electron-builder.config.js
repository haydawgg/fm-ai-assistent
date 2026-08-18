module.exports = {
  appId: 'com.github.fmaiassistent',
  productName: 'FM AI Assistent',
  copyright: 'Copyright (c) 2026 haydawgg',
  directories: {
    output: 'electron/dist',
    buildResources: 'electron/assets',
  },
  files: ['electron/**/*', 'package.json'],
  extraResources: [
    {
      from: 'electron/resources',
      to: '.',
      filter: ['**/*.jar'],
    },
  ],
  win: {
    target: [
      { target: 'nsis', arch: ['x64'] },
      { target: 'portable', arch: ['x64'] },
    ],
    icon: 'electron/assets/icon.png',
  },
  nsis: {
    oneClick: false,
    allowToChangeInstallationDirectory: true,
    createDesktopShortcut: true,
    createStartMenuShortcut: true,
    shortcutName: 'FM AI Assistent',
  },
  mac: {
    target: [{ target: 'dmg', arch: ['x64', 'arm64'] }],
    icon: 'electron/assets/icon.png',
    category: 'public.app-category.sports',
  },
  linux: {
    target: [{ target: 'AppImage', arch: ['x64'] }],
    icon: 'electron/assets/icon.png',
    category: 'Game',
    maintainer: 'haydawgg',
  },
  publish: null,
  npmRebuild: false,
  electronDownload: {
    mirror: process.env.ELECTRON_MIRROR || undefined,
  },
};