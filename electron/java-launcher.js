import { app } from 'electron';
import { spawn, execFile } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BACKEND_URL = process.env.FM_AI_BACKEND_URL || 'http://127.0.0.1:8080';
const JVM_ARGS = ['-Xms256m', '-Xmx2g', '--enable-native-access=ALL-UNNAMED'];

let javaProcess = null;
let stopping = false;
let backendState = { state: 'idle', external: false, error: null };

function javaCandidates() {
  const candidates = [];
  const addIfExists = (candidate) => {
    if (candidate && fs.existsSync(candidate)) {
      candidates.push(candidate);
    }
  };
  if (process.env.JAVA_HOME) {
    addIfExists(path.join(process.env.JAVA_HOME, 'bin', 'java.exe'));
    addIfExists(path.join(process.env.JAVA_HOME, 'bin', 'java'));
  }
  if (process.platform === 'win32') {
    addIfExists('C:\\Program Files\\Eclipse Adoptium\\jdk-25\\bin\\java.exe');
    addIfExists('C:\\Program Files\\Java\\jdk-25\\bin\\java.exe');
    for (const base of ['C:\\Program Files\\Eclipse Adoptium', 'C:\\Program Files\\Java']) {
      if (!fs.existsSync(base)) {
        continue;
      }
      for (const entry of fs.readdirSync(base)) {
        if (entry.toLowerCase().startsWith('jdk-25') || entry.toLowerCase().startsWith('temurin')) {
          addIfExists(path.join(base, entry, 'bin', 'java.exe'));
        }
      }
    }
    const pathEntries = (process.env.PATH || '').split(';');
    for (const entry of pathEntries) {
      if (/java/i.test(entry)) {
        addIfExists(path.join(entry, 'java.exe'));
      }
    }
  } else {
    candidates.push('/usr/bin/java', '/usr/local/bin/java', '/opt/java/bin/java');
  }
  return candidates;
}

function findJava() {
  const found = javaCandidates();
  return found[0] || null;
}

function findJar() {
  if (app.isPackaged) {
    const matches = fs
      .readdirSync(process.resourcesPath)
      .filter((file) => /^fm-ai-assistent-[\d.]+(-SNAPSHOT)?\.jar$/.test(file));
    if (matches.length > 0) {
      return path.join(process.resourcesPath, matches[0]);
    }
  }
  const dev = path.join(__dirname, '..', 'target');
  if (fs.existsSync(dev)) {
    const matches = fs.readdirSync(dev).filter((file) => /^fm-ai-assistent-[\d.]+(-SNAPSHOT)?\.jar$/.test(file));
    if (matches.length > 0) {
      return path.join(dev, matches[0]);
    }
  }
  return null;
}

function isBackendAlreadyRunning() {
  return new Promise((resolve) => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2000);
    fetch(BACKEND_URL, { signal: controller.signal })
      .then((response) => {
        clearTimeout(timer);
        resolve(response.status < 500);
      })
      .catch(() => {
        clearTimeout(timer);
        resolve(false);
      });
  });
}

function killProcessTree(pid) {
  if (process.platform === 'win32') {
    execFile('taskkill', ['/pid', String(pid), '/T', '/F'], () => {});
  } else {
    try {
      process.kill(pid, 'SIGTERM');
    } catch {
      // already gone
    }
  }
}

export function getBackendState() {
  return backendState;
}

export async function startBackend() {
  if (javaProcess) {
    return true;
  }
  if (await isBackendAlreadyRunning()) {
    backendState = { state: 'ready', external: true, error: null };
    return false;
  }

  const java = findJava();
  if (!java) {
    backendState = { state: 'error', external: false, error: 'Java 25 was not found. Install Eclipse Temurin JDK 25.' };
    return false;
  }

  const jar = findJar();
  if (!jar) {
    backendState = {
      state: 'error',
      external: false,
      error: 'Backend JAR not found. Run "mvn package" (or "mvn -Pelectron package") first.',
    };
    return false;
  }

  backendState = { state: 'starting', external: false, error: null };

  return new Promise((resolve) => {
    javaProcess = spawn(java, [...JVM_ARGS, '-jar', jar], {
      detached: false,
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true,
    });

    javaProcess.stdout.on('data', (data) => {
      process.stdout.write(`[java] ${data}`);
    });
    javaProcess.stderr.on('data', (data) => {
      process.stderr.write(`[java] ${data}`);
    });

    javaProcess.on('error', (error) => {
      backendState = { state: 'error', external: false, error: `Failed to launch Java: ${error.message}` };
      javaProcess = null;
      resolve(false);
    });

    javaProcess.on('exit', (code) => {
      if (!stopping) {
        backendState = { state: 'error', external: false, error: `Java backend exited unexpectedly (code ${code})` };
      }
      javaProcess = null;
    });

    resolve(true);
  });
}

export function stopBackend() {
  stopping = true;
  if (javaProcess && javaProcess.pid) {
    killProcessTree(javaProcess.pid);
  }
  javaProcess = null;
}