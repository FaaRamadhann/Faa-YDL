const socket = io();

const els = {
  urlInput: document.getElementById('url-input'),
  formatSelect: document.getElementById('format-select'),
  btnDownload: document.getElementById('btn-download'),
  qrFormat: document.getElementById('qr-format'),
  btnQrDownload: document.getElementById('btn-qr-download'),
  progressArea: document.getElementById('progress-area'),
  progressFill: document.getElementById('progress-fill'),
  progressPct: document.getElementById('progress-pct'),
  statusText: document.getElementById('status-text'),
  logArea: document.getElementById('log-area'),
  qrResult: document.getElementById('qr-result'),
  qrUrl: document.getElementById('qr-url'),
  qrUrlInput: document.getElementById('qr-url-input'),
  qrCameraError: document.getElementById('qr-camera-error'),
  btnPermitCamera: document.getElementById('btn-permit-camera'),
  qrImageInput: document.getElementById('qr-image-input'),
  qrCanvas: document.getElementById('qr-canvas'),
  httpsInfo: document.getElementById('https-info'),
  httpsUrl: document.getElementById('https-url'),
  btnCopyUrl: document.getElementById('btn-copy-url'),
  historyList: document.getElementById('history-list'),
  tabBtns: document.querySelectorAll('.tab-btn'),
  tabContents: document.querySelectorAll('.tab-content')
};

let scanning = false;
let qrScanner = null;
let scannedUrl = '';
let isDownloading = false;

function addLog(msg, type = 'info') {
  const el = document.createElement('div');
  el.className = `log-entry ${type}`;
  el.textContent = msg;
  els.logArea.appendChild(el);
  el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function showProgress(show) {
  els.progressArea.classList.toggle('hidden', !show);
}

function setProgress(pct) {
  const p = Math.min(100, Math.max(0, pct));
  els.progressFill.style.width = p + '%';
  els.progressPct.textContent = p + '%';
}

function setStatus(msg) {
  els.statusText.textContent = msg;
}

function resetUI() {
  showProgress(false);
  setProgress(0);
  setStatus('');
  isDownloading = false;
  els.btnDownload.disabled = false;
  els.btnQrDownload.disabled = false;
}

els.tabBtns.forEach(btn => {
  btn.addEventListener('click', () => {
    els.tabBtns.forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    els.tabContents.forEach(tc => tc.classList.remove('active'));
    document.getElementById('tab-' + btn.dataset.tab).classList.add('active');

    if (btn.dataset.tab !== 'qr') {
      stopScanner();
    } else {
      startScanner();
    }

    if (btn.dataset.tab === 'history') {
      loadHistory();
    }
  });
});

els.btnDownload.addEventListener('click', () => {
  const url = els.urlInput.value.trim();
  const format = els.formatSelect.value;
  if (!url) {
    addLog('Masukkan URL YouTube terlebih dahulu', 'error');
    els.urlInput.focus();
    return;
  }
  startDownload(url, format);
});

els.btnPermitCamera.addEventListener('click', async () => {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true });
    stream.getTracks().forEach(t => t.stop());
    addLog('Izin kamera diberikan! Memulai scanner...', 'success');
    startScanner();
  } catch (e) {
    addLog('Izin kamera ditolak: ' + e.message, 'error');
    addLog('Gunakan scan dari gambar atau input manual.', 'info');
  }
});

els.qrImageInput.addEventListener('change', (e) => {
  const file = e.target.files[0];
  if (!file) return;

  const reader = new FileReader();
  reader.onload = (ev) => {
    const img = new Image();
    img.onload = () => {
      const canvas = els.qrCanvas;
      canvas.width = img.width;
      canvas.height = img.height;
      const ctx = canvas.getContext('2d');
      ctx.drawImage(img, 0, 0);
      const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
      const code = jsQR(imageData.data, imageData.width, imageData.height);
      if (code) {
        scannedUrl = code.data;
        els.qrUrl.textContent = code.data;
        els.qrUrlInput.value = code.data;
        els.qrResult.classList.remove('hidden');
        addLog('QR Code terbaca dari gambar!', 'success');
      } else {
        addLog('Tidak ada QR code yang terdeteksi di gambar', 'error');
      }
    };
    img.src = ev.target.result;
  };
  reader.readAsDataURL(file);
});

els.btnQrDownload.addEventListener('click', () => {
  const url = scannedUrl || els.qrUrlInput.value.trim();
  if (!url) {
    addLog('Masukkan URL atau scan QR code terlebih dahulu', 'error');
    els.qrUrlInput.focus();
    return;
  }
  const format = els.qrFormat.value;
  startDownload(url, format);
});

els.qrUrlInput.addEventListener('input', () => {
  if (els.qrUrlInput.value.trim()) {
    scannedUrl = '';
    els.qrResult.classList.add('hidden');
  }
});

els.qrUrlInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    els.btnQrDownload.click();
  }
});

document.getElementById('btn-clear-log').addEventListener('click', () => {
  els.logArea.innerHTML = '';
});

els.urlInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    els.btnDownload.click();
  }
});

document.getElementById('btn-clear-history').addEventListener('click', async () => {
  if (!confirm('Hapus semua file download?')) return;
  try {
    await fetch('/api/clear-history', { method: 'POST' });
    els.historyList.innerHTML = '<p class="empty-msg">Belum ada file yang diunduh.</p>';
    addLog('History dibersihkan', 'success');
  } catch {
    addLog('Gagal membersihkan history', 'error');
  }
});

els.btnCopyUrl.addEventListener('click', () => {
  const url = els.httpsUrl.textContent;
  navigator.clipboard.writeText(url).then(() => {
    addLog('URL HTTPS disalin!', 'success');
  }).catch(() => {
    addLog('Gagal menyalin, salin manual: ' + url, 'info');
  });
});

async function fetchServerInfo() {
  try {
    const resp = await fetch('/api/info');
    const data = await resp.json();
    if (data.httpsUrl) {
      els.httpsUrl.textContent = data.httpsUrl;
      els.httpsInfo.classList.remove('hidden');
    }
  } catch {}
}
fetchServerInfo();

function startDownload(url, format) {
  if (isDownloading) return;
  isDownloading = true;

  els.btnDownload.disabled = true;
  els.btnQrDownload.disabled = true;
  els.logArea.innerHTML = '';
  showProgress(true);
  setStatus('Memulai download...');
  setProgress(0);

  addLog(`URL: ${url}`, 'info');
  addLog(`Format: ${format === 'mp3' ? 'MP3 (Audio)' : 'Video (MP4)'}`, 'info');

  socket.emit('download', { url, format });
}

socket.on('status', (data) => {
  setStatus(data.message);
  addLog(data.message, data.type || 'info');
  if (data.type === 'success') {
    resetUI();
    loadHistory();
  }
});

socket.on('progress', (pct) => {
  setProgress(pct);
});

socket.on('error', (msg) => {
  addLog('Error: ' + msg, 'error');
  resetUI();
});

socket.on('disconnect', () => {
  addLog('Koneksi terputus. Refresh halaman.', 'error');
});

function startScanner() {
  if (scanning) return;

  const readerEl = document.getElementById('qr-reader');
  els.qrCameraError.classList.add('hidden');

  if (!readerEl) return;

  try {
    qrScanner = new Html5Qrcode("qr-reader");

    qrScanner.start(
      { facingMode: "environment" },
      {
        fps: 15,
        qrbox: { width: 250, height: 250 }
      },
      (decodedText) => {
        scannedUrl = decodedText;
        els.qrUrl.textContent = decodedText;
        els.qrUrlInput.value = decodedText;
        els.qrResult.classList.remove('hidden');
        addLog('QR Code terdeteksi: ' + decodedText, 'success');
        stopScanner();
      },
      () => {}
    ).then(() => {
      scanning = true;
      addLog('Kamera aktif. Arahkan QR code ke kamera.', 'info');
    }).catch((err) => {
      els.qrCameraError.classList.remove('hidden');
      addLog('Kamera tidak bisa diakses', 'error');
      addLog('Klik "Minta Izin Kamera" atau scan dari gambar', 'info');
      stopScanner();
    });
  } catch (e) {
    els.qrCameraError.classList.remove('hidden');
    addLog('QR Scanner tidak tersedia', 'error');
    addLog('Gunakan scan dari gambar atau input manual', 'info');
  }
}

async function requestCameraPermission() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true });
    stream.getTracks().forEach(t => t.stop());
    return true;
  } catch {
    return false;
  }
}

function stopScanner() {
  if (qrScanner) {
    try {
      qrScanner.stop().then(() => {
        qrScanner.clear();
      }).catch(() => {});
    } catch (e) {}
    qrScanner = null;
  }
  scanning = false;
}

async function loadHistory() {
  try {
    const resp = await fetch('/api/downloads');
    const data = await resp.json();

    if (!data.files || data.files.length === 0) {
      els.historyList.innerHTML = '<p class="empty-msg">Belum ada file yang diunduh.</p>';
      return;
    }

    els.historyList.innerHTML = data.files.map(f => {
      const size = formatSize(f.size);
      const time = new Date(f.time).toLocaleString('id-ID');
      return `<div class="file-item">
        <div class="file-info">
          <div class="file-name">${escapeHtml(f.name)}</div>
          <div class="file-meta">${time}</div>
        </div>
        <div class="file-size">${size}</div>
      </div>`;
    }).join('');
  } catch (e) {
    els.historyList.innerHTML = '<p class="empty-msg">Gagal memuat history.</p>';
  }
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}
