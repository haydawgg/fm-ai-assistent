import { app } from 'electron';
import { spawn, execFile, spawnSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
let BACKEND_URL = process.env.FM_AI_BACKEND_URL || 'http://127.0.0.1:8080';
try {
  new URL(BACKEND_URL);
} catch {
  BACKEND_URL = 'http://127.0.0.1:8080';
}
const JVM_ARGS = ['-Xms256m', '-Xmx2g', '--enable-native-access=ALL-UNNAMED'];
const SPRING_ARGS = [
  '--management.endpoint.shutdown.enabled=true',
  '--management.endpoints.web.exposure.include=health,info,shutdown',
];

let javaProcess = null;
let stopping = false;
let startPromise = null;
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
    addIfExists('/usr/bin/java');
    addIfExists('/usr/local/bin/java');
    addIfExists('/opt/java/bin/java');
    addIfExists('/opt/jdk-25/bin/java');
    addIfExists('/usr/lib/jvm/default-java/bin/java');
    addIfExists('/usr/lib/jvm/java-25-openjdk/bin/java');
  }
  return candidates;
}

function findJava() {
  const found = javaCandidates();
  return found[0] || null;
}

function findJar() {
  try {
    if (app.isPackaged) {
      const matches = fs
        .readdirSync(process.resourcesPath)
        .filter((file) => /^fm-ai-assistent-[\d.]+(-SNAPSHOT)?\.jar$/.test(file));
      if (matches.length > 0) {
        return path.join(process.resourcesPath, selectJar(matches));
      }
    }
    const dev = path.join(__dirname, '..', 'target');
    if (fs.existsSync(dev)) {
      const matches = fs.readdirSync(dev).filter((file) => /^fm-ai-assistent-[\d.]+(-SNAPSHOT)?\.jar$/.test(file));
      if (matches.length > 0) {
        return path.join(dev, selectJar(matches));
      }
    }
  } catch {
    // ignore read errors; fall through to null
  }
  return null;
}

function selectJar(matches) {
  return [...matches].sort((left, right) => {
    const version = (name) => name.match(/^fm-ai-assistent-([\d.]+)(-SNAPSHOT)?\.jar$/);
    const a = version(left);
    const b = version(right);
    const compareVersion = (x, y) => {
      const xs = x.split('.').map(Number);
      const ys = y.split('.').map(Number);
      for (let i = 0; i < Math.max(xs.length, ys.length); i++) {
        const result = (xs[i] || 0) - (ys[i] || 0);
        if (result !== 0) return result;
      }
      return 0;
    };
    return compareVersion(b[1], a[1]) || (a[2] ? 1 : 0) - (b[2] ? 1 : 0) || left.localeCompare(right);
  })[0];
}

function isBackendAlreadyRunning() {
  return new Promise((resolve) => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2000);
    fetch(BACKEND_URL, { signal: controller.signal })
      .then(async (response) => {
        clearTimeout(timer);
        if (response.status >= 500) {
          // A server is mid-boot or unhealthy on this port. Treat the port as
          // occupied so we don't spawn a second JVM that races for it.
          resolve(true);
          return;
        }
        const text = await response.text().catch(() => '');
        // Only adopt the port if it is really our Vaadin app, not some other service.
        resolve(/vaadin/i.test(text) || /fm-ai-assistent/i.test(text));
      })
      .catch(() => {
        clearTimeout(timer);
        resolve(false);
      });
  });
}

function killProcessTree(pid) {
  if (process.platform === 'win32') {
    return new Promise((resolve) => {
      execFile('taskkill', ['/pid', String(pid), '/T', '/F'], () => resolve());
    });
  }
  return new Promise((resolve) => {
    try {
      process.kill(pid, 'SIGTERM');
    } catch {
      // already gone
    }
    resolve();
  });
}

export function getBackendState() {
  return backendState;
}

export function markBackendReady() {
  if (backendState.state === 'starting') {
    backendState = { state: 'ready', external: false, error: null };
  }
}

function javaMajorVersion(java) {
  try {
    const { stderr, stdout, status } = spawnSync(java, ['-version'], {
      encoding: 'utf8',
      timeout: 15_000,
      windowsHide: true,
    });
    if (status !== 0) {
      return null;
    }
    const match = `${stderr}\n${stdout}`.match(/version\s+"(\d+)/);
    return match ? Number(match[1]) : null;
  } catch {
    return null;
  }
}

export async function startBackend() {
  if (javaProcess) {
    return true;
  }
  if (startPromise) {
    return startPromise;
  }
  startPromise = doStartBackend().finally(() => {
    startPromise = null;
  });
  return startPromise;
}

async function doStartBackend() {
  if (javaProcess) {
    return true;
  }
  stopping = false;
  if (await isBackendAlreadyRunning()) {
    backendState = { state: 'ready', external: true, error: null };
    return false;
  }

  const java = findJava();
  if (!java) {
    backendState = { state: 'error', external: false, error: 'Java 25 was not found. Install Eclipse Temurin JDK 25.' };
    return false;
  }

  const major = javaMajorVersion(java);
  if (major !== null && major < 25) {
    backendState = {
      state: 'error',
      external: false,
      error: `Java ${major} was found, but FM AI Assistent requires Java 25. Install Eclipse Temurin JDK 25.`,
    };
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
    javaProcess = spawn(java, [...JVM_ARGS, '-jar', jar, ...SPRING_ARGS], {
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
  const proc = javaProcess;
  if (!proc || !proc.pid || proc.exitCode !== null) {
    return Promise.resolve();
  }
  // Ask Spring Boot to shut down gracefully first so H2 and exports flush cleanly.
  fetch(`${BACKEND_URL}/actuator/shutdown`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: '{}',
    signal: AbortSignal.timeout(3000),
  }).catch(() => {});
  return new Promise((resolve) => {
    let finished = false;
    const finish = () => {
      if (!finished) {
        finished = true;
        clearTimeout(forceTimer);
        clearTimeout(abandonTimer);
        resolve();
      }
    };
    const forceTimer = setTimeout(() => {
      killProcessTree(proc.pid).then(finish);
    }, 12_000);
    const abandonTimer = setTimeout(() => {
      killProcessTree(proc.pid).then(finish);
    }, 25_000);
    // Guard against a race where the process exits between the exitCode check and listener attach.
    proc.once('exit', () => {
      finish();
    });
  });
}
