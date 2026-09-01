#!/usr/bin/env node
const { execSync } = require('child_process');

const PORTS = [8001, 8080, 8081, 8082, 5173, 5174, 5175, 3000, 5000, 19000, 19006];

console.log('🧹 [Vidyut] Clearing ports:', PORTS.join(', '));

const isWindows = process.platform === 'win32';

let freedCount = 0;

for (const port of PORTS) {
  try {
    if (isWindows) {
      // Find PID on Windows
      const output = execSync(`netstat -ano | findstr :${port}`, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] });
      const lines = output.trim().split('\n');
      const pids = new Set();
      
      for (const line of lines) {
        if (line.includes('LISTENING') || line.includes('ESTABLISHED')) {
          const parts = line.trim().split(/\s+/);
          const pid = parts[parts.length - 1];
          if (pid && !isNaN(pid) && pid !== '0' && pid !== String(process.pid)) {
            pids.add(pid);
          }
        }
      }

      for (const pid of pids) {
        try {
          execSync(`taskkill /F /PID ${pid}`, { stdio: 'ignore' });
          console.log(`  ✓ Killed process PID ${pid} listening on port ${port}`);
          freedCount++;
        } catch {}
      }
    } else {
      // macOS / Linux
      const output = execSync(`lsof -ti :${port}`, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] });
      const pids = output.trim().split('\n').filter(Boolean);
      for (const pid of pids) {
        if (pid !== String(process.pid)) {
          execSync(`kill -9 ${pid}`, { stdio: 'ignore' });
          console.log(`  ✓ Killed process PID ${pid} listening on port ${port}`);
          freedCount++;
        }
      }
    }
  } catch (err) {
    // Port was already free
  }
}

if (freedCount === 0) {
  console.log('✨ All ports are already free and ready to use!');
} else {
  console.log(`✅ Successfully freed ${freedCount} port process(es). Ready to start fresh!`);
}
