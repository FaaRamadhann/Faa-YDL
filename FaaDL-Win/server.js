const express = require('express');
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const http = require('http');
const https = require('https');
const forge = require('node-forge');
const { Server } = require('socket.io');

const app = express();

const HTTP_PORT = 2080;
const HTTPS_PORT = 2081;
// Hasil download: %USERPROFILE%\Downloads\FaaDL (Windows) atau
// ~/Downloads/FaaDL (Linux). Fallback ke folder downloads/ lokal.
const os = require('os');
const HOME_DL = path.join(os.homedir(), 'Downloads', 'FaaDL');
const LOCAL_DIR = path.join(__dirname, 'downloads');
const DOWNLOAD_DIR = (() => {
  try {
    fs.mkdirSync(HOME_DL, { recursive: true });
    return HOME_DL;
  } catch (e) {
    return LOCAL_DIR;
  }
})();

if (!fs.existsSync(DOWNLOAD_DIR)) {
  fs.mkdirSync(DOWNLOAD_DIR, { recursive: true });
}

app.use(express.static(path.join(__dirname, 'public')));
app.use(express.json());

app.get('/api/downloads', (req, res) => {
  fs.readdir(DOWNLOAD_DIR, (err, files) => {
    if (err) return res.json({ files: [] });
    const fileList = files
      .filter(f => !f.startsWith('.') && fs.statSync(path.join(DOWNLOAD_DIR, f)).isFile())
      .map(f => ({
        name: f,
        size: fs.statSync(path.join(DOWNLOAD_DIR, f)).size,
        time: fs.statSync(path.join(DOWNLOAD_DIR, f)).mtime
      }))
      .sort((a, b) => b.time - a.time);
    res.json({ files: fileList });
  });
});

app.post('/api/clear-history', (req, res) => {
  fs.readdir(DOWNLOAD_DIR, (err, files) => {
    if (err) return res.json({ ok: false });
    let deleted = 0;
    files.forEach(f => {
      const fp = path.join(DOWNLOAD_DIR, f);
      try {
        if (fs.statSync(fp).isFile()) {
          fs.unlinkSync(fp);
          deleted++;
        }
      } catch {}
    });
    res.json({ ok: true, deleted });
  });
});

app.get('/api/info', (req, res) => {
  res.json({
    ip: getLocalIP(),
    httpPort: HTTP_PORT,
    httpsPort: HTTPS_PORT,
    httpsUrl: `https://${getLocalIP()}:${HTTPS_PORT}`
  });
});

function createServer(protocol) {
  let srv;
  if (protocol === 'https') {
    const pems = generateCert();
    srv = https.createServer({ key: pems.private, cert: pems.cert }, app);
  } else {
    srv = http.createServer(app);
  }

  const io = new Server(srv);

  io.on('connection', (socket) => {
    console.log(`Client connected: ${socket.id}`);

    socket.on('download', (data) => {
      const { url, format } = data;
      if (!url || !url.trim()) {
        socket.emit('error', 'URL tidak boleh kosong');
        return;
      }

      const fmt = format || 'mp3';
      const outputTemplate = path.join(DOWNLOAD_DIR, '%(title)s.%(ext)s');

      const args = [
        '--no-playlist',
        '--ignore-errors',
        '--extractor-args', 'youtube:player_client=android',
        '-o', outputTemplate
      ];

      if (fmt === 'mp3') {
        args.push('-x', '--audio-format', 'mp3', '--audio-quality', '192K');
      } else {
        args.push('-f', 'best');
      }

      args.push(url);

      socket.emit('status', { message: 'Memproses URL...', type: 'info' });

      const proc = spawn('yt-dlp', args, { cwd: DOWNLOAD_DIR });

      proc.stdout.on('data', (data) => {
        const line = data.toString();

        const titleMatch = line.match(/\[download\] Destination: (.+)/);
        if (titleMatch) {
          const title = path.basename(titleMatch[1].trim()).replace(/\.\w+$/, '');
          socket.emit('status', { message: `Mengunduh: ${title}`, type: 'info' });
        }

        const pctMatch = line.match(/(\d+\.?\d*)%/);
        if (pctMatch) {
          socket.emit('progress', parseFloat(pctMatch[1]));
        }
      });

      proc.stderr.on('data', (data) => {
        const line = data.toString();
        if (line.includes('ERROR:')) {
          socket.emit('error', line.replace('ERROR:', '').trim());
        }
      });

      proc.on('close', (code) => {
        if (code === 0) {
          socket.emit('status', { message: 'Selesai! File tersimpan di folder downloads', type: 'success' });
          socket.emit('progress', 100);
        } else if (code !== 0) {
          socket.emit('error', `Proses gagal dengan kode: ${code}`);
        }
      });

      proc.on('error', (err) => {
        socket.emit('error', `Gagal memulai proses: ${err.message}`);
      });
    });

    socket.on('disconnect', () => {
      console.log(`Client disconnected: ${socket.id}`);
    });
  });

  return srv;
}

function generateCert() {
  const keys = forge.pki.rsa.generateKeyPair(2048);
  const cert = forge.pki.createCertificate();
  cert.publicKey = keys.publicKey;
  cert.serialNumber = '01' + Date.now().toString(16);
  cert.validity.notBefore = new Date();
  cert.validity.notAfter = new Date();
  cert.validity.notAfter.setFullYear(cert.validity.notBefore.getFullYear() + 1);

  const attrs = [{ name: 'commonName', value: 'localhost' }];
  cert.setSubject(attrs);
  cert.setIssuer(attrs);

  cert.setExtensions([
    { name: 'basicConstraints', cA: true },
    { name: 'keyUsage', keyCertSign: true, digitalSignature: true, keyEncipherment: true },
    { name: 'extKeyUsage', serverAuth: true },
    { name: 'subjectAltName', altNames: [{ type: 2, value: 'localhost' }, { type: 7, ip: '127.0.0.1' }] }
  ]);

  cert.sign(keys.privateKey);

  return {
    private: forge.pki.privateKeyToPem(keys.privateKey),
    cert: forge.pki.certificateToPem(cert)
  };
}

function getLocalIP() {
  const nets = require('os').networkInterfaces();
  for (const name of Object.keys(nets)) {
    for (const net of nets[name]) {
      if (net.family === 'IPv4' && !net.internal) return net.address;
    }
  }
  return '127.0.0.1';
}

const ip = getLocalIP();

const httpServer = createServer('http');
httpServer.listen(HTTP_PORT, '0.0.0.0', () => {
  console.log(`HTTP  : http://localhost:${HTTP_PORT}`);
  console.log(`         http://${ip}:${HTTP_PORT}`);
});

try {
  const httpsServer = createServer('https');
  httpsServer.listen(HTTPS_PORT, '0.0.0.0', () => {
    console.log(`HTTPS : https://localhost:${HTTPS_PORT}`);
    console.log(`         https://${ip}:${HTTPS_PORT}`);
    console.log(`\nGunakan HTTPS untuk scan QR dari HP:`);
    console.log(`  https://${ip}:${HTTPS_PORT}`);
    console.log(`Abaikan peringatan "Not Secure" / "Privacy error" di browser,`);
    console.log(`lalu klik "Advanced" > "Proceed to localhost (unsafe)".`);
  });
} catch (e) {
  console.log(`\nHTTPS gagal: ${e.message}`);
  console.log('QR scanner hanya via input manual.');
}
