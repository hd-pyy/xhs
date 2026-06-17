// XHS Downloader Web 版前端 —— 单文件 vanilla JS。
// 「下载到本机」:解析后对每个 mediaUrl 创建 <a href="/api/fetch?url=..." download>,
// 浏览器原生下载框依次弹;服务端不落盘。
// 「存到服务端」:走 /api/download,文件落 web/storage/,前端可浏览历史。

const $ = (id) => document.getElementById(id);
const textInput = $('text-input');
const btnParse = $('btn-parse');
const btnDownload = $('btn-download');
const btnServer = $('btn-server');
const btnRefreshHistory = $('btn-refresh-history');
const statusEl = $('status');
const resultsSection = $('results-section');
const resultsEl = $('results');
const savedSection = $('saved-section');
const savedFilesEl = $('saved-files');
const historyList = $('history-list');

function setStatus(msg, kind = '') {
  statusEl.textContent = msg;
  statusEl.className = 'status' + (kind ? ' ' + kind : '');
}

function renderMediaItem(url, isVideo) {
  const div = document.createElement('div');
  div.className = 'media-item';
  if (isVideo) {
    const v = document.createElement('video');
    v.src = url;
    v.controls = true;
    v.muted = true;
    v.playsInline = true;
    div.appendChild(v);
  } else {
    const img = document.createElement('img');
    img.src = url;
    img.loading = 'lazy';
    img.referrerPolicy = 'no-referrer';
    div.appendChild(img);
  }
  const meta = document.createElement('div');
  meta.className = 'meta';
  meta.textContent = url;
  div.appendChild(meta);
  return div;
}

function looksLikeVideo(url) {
  const lower = url.toLowerCase();
  return lower.includes('.mp4') || lower.includes('.mov') || lower.includes('video') || lower.includes('masterurl');
}

async function callParse() {
  const text = textInput.value.trim();
  if (!text) {
    setStatus('请先粘贴小红书分享文案或链接', 'error');
    return;
  }
  setStatus('解析中…');
  btnParse.disabled = true;
  btnDownload.disabled = true;
  btnServer.disabled = true;
  try {
    const res = await fetch('/api/parse', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    });
    const json = await res.json();
    if (!res.ok) {
      setStatus(json.error ?? '解析失败', 'error');
      resultsSection.hidden = true;
      return;
    }
    renderParseResults(json.results ?? []);
    setStatus(`解析完成:${(json.results ?? []).length} 条`, 'ok');
  } catch (e) {
    setStatus('解析异常: ' + (e?.message ?? e), 'error');
  } finally {
    btnParse.disabled = false;
    btnDownload.disabled = false;
    btnServer.disabled = false;
  }
}

function renderParseResults(results) {
  resultsEl.innerHTML = '';
  if (!results.length) {
    resultsSection.hidden = true;
    return;
  }
  for (const note of results) {
    const block = document.createElement('div');
    block.className = 'note-block';
    const urlDiv = document.createElement('div');
    urlDiv.className = 'url';
    urlDiv.textContent = `${note.postId ?? '(no postId)'} — ${note.originalUrl}`;
    block.appendChild(urlDiv);

    if (note.error) {
      const e = document.createElement('div');
      e.className = 'status error';
      e.textContent = note.error;
      block.appendChild(e);
    }

    const grid = document.createElement('div');
    grid.className = 'media-grid';
    for (const u of note.mediaUrls) {
      grid.appendChild(renderMediaItem(u, looksLikeVideo(u)));
    }
    block.appendChild(grid);
    resultsEl.appendChild(block);
  }
  resultsSection.hidden = false;
}

/**
 * 「下载到本机」:跑一次 /api/parse 拿到所有 mediaUrl + noteId,
 * 然后循环给每个 URL 创建 <a href="/api/fetch?url=...&name=..." download>,
 * Chrome 会依次弹原生下载框,每个文件落本机「下载」目录,服务端不落盘。
 */
async function callDownload() {
  const text = textInput.value.trim();
  if (!text) {
    setStatus('请先粘贴小红书分享文案或链接', 'error');
    return;
  }
  setStatus('解析并逐个触发下载中…');
  btnParse.disabled = true;
  btnDownload.disabled = true;
  btnServer.disabled = true;
  try {
    const res = await fetch('/api/parse', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    });
    const json = await res.json();
    if (!res.ok) {
      setStatus(json.error ?? '解析失败', 'error');
      return;
    }
    const results = json.results ?? [];
    // 拍平:每条 note 的每个 mediaUrl 配一个文件名
    const tasks = [];
    let counter = 0;
    for (const note of results) {
      const postId = note.postId || `note_${++counter}`;
      for (let i = 0; i < (note.mediaUrls ?? []).length; i++) {
        const u = note.mediaUrls[i];
        const ext = inferExt(u);
        const name = `${postId}_${String(i + 1).padStart(2, '0')}.${ext}`;
        tasks.push({ url: u, name });
      }
    }
    if (tasks.length === 0) {
      setStatus('解析后没有任何媒体可下载', 'error');
      return;
    }

    setStatus(`正在依次下载 ${tasks.length} 个文件(浏览器会逐个弹框)…`, 'ok');
    for (let i = 0; i < tasks.length; i++) {
      const t = tasks[i];
      const a = document.createElement('a');
      a.href = `/api/fetch?url=${encodeURIComponent(t.url)}&name=${encodeURIComponent(t.name)}`;
      a.download = t.name;
      a.style.display = 'none';
      document.body.appendChild(a);
      a.click();
      a.remove();
      // 错开点击,Chrome 不会拦下载;每个之间留 250ms
      if (i < tasks.length - 1) {
        await new Promise((r) => setTimeout(r, 250));
      }
    }
    setStatus(`已触发 ${tasks.length} 个文件下载,请在浏览器下载框/下载目录查看`, 'ok');
  } catch (e) {
    setStatus('下载异常: ' + (e?.message ?? e), 'error');
  } finally {
    btnParse.disabled = false;
    btnDownload.disabled = false;
    btnServer.disabled = false;
  }
}

function inferExt(url) {
  const lower = url.toLowerCase();
  if (lower.includes('.mp4') || lower.includes('masterurl') || lower.includes('stream')) return 'mp4';
  if (lower.includes('.png')) return 'png';
  if (lower.includes('.gif')) return 'gif';
  if (lower.includes('.webp')) return 'webp';
  return 'jpg';
}

async function callServerSave() {
  const text = textInput.value.trim();
  if (!text) {
    setStatus('请先粘贴小红书分享文案或链接', 'error');
    return;
  }
  setStatus('存到服务端中,请稍候(可能耗时 30s+)…');
  btnParse.disabled = true;
  btnDownload.disabled = true;
  btnServer.disabled = true;
  try {
    const res = await fetch('/api/download', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    });
    const json = await res.json();
    if (!res.ok) {
      setStatus(json.error ?? '下载失败', 'error');
      return;
    }
    renderSavedFiles(json.savedFiles ?? []);
    setStatus(
      json.ok ? `已落盘 ${(json.savedFiles ?? []).length} 个文件到服务端 web/storage/` : '下载未成功',
      json.ok ? 'ok' : 'error',
    );
    await loadHistory();
  } catch (e) {
    setStatus('下载异常: ' + (e?.message ?? e), 'error');
  } finally {
    btnParse.disabled = false;
    btnDownload.disabled = false;
    btnServer.disabled = false;
  }
}

function renderSavedFiles(files) {
  savedFilesEl.innerHTML = '';
  if (!files.length) {
    savedSection.hidden = true;
    return;
  }
  const grid = document.createElement('div');
  grid.className = 'media-grid';
  for (const f of files) {
    const div = renderMediaItem(f.publicUrl, f.isVideo);
    const meta = div.querySelector('.meta');
    meta.innerHTML = '';
    const name = document.createElement('div');
    name.textContent = f.fileName + (f.isLivePhoto ? ' (Live Photo fallback)' : '');
    meta.appendChild(name);
    const a = document.createElement('a');
    a.href = f.publicUrl;
    a.download = f.fileName;
    a.textContent = '下载';
    a.style.marginRight = '6px';
    meta.appendChild(a);
    grid.appendChild(div);
  }
  savedFilesEl.appendChild(grid);
  savedSection.hidden = false;
}

async function loadHistory() {
  try {
    const res = await fetch('/api/history');
    const json = await res.json();
    historyList.innerHTML = '';
    const items = json.items ?? [];
    if (!items.length) {
      historyList.innerHTML = '<div class="status">还没有下载文件</div>';
      return;
    }
    for (const f of items) {
      const row = document.createElement('div');
      row.className = 'history-row';
      const a = document.createElement('a');
      a.href = f.publicUrl;
      a.download = f.fileName;
      a.textContent = f.fileName;
      row.appendChild(a);
      const tag = document.createElement('span');
      tag.className = 'tag' + (f.isVideo ? ' video' : '');
      tag.textContent = f.isVideo ? 'VIDEO' : 'IMAGE';
      row.appendChild(tag);
      const size = document.createElement('span');
      size.className = 'status';
      size.textContent = (f.size / 1024).toFixed(1) + ' KB';
      row.appendChild(size);
      historyList.appendChild(row);
    }
  } catch (e) {
    historyList.innerHTML = '<div class="status error">历史加载失败</div>';
  }
}

btnParse.addEventListener('click', callParse);
btnDownload.addEventListener('click', callDownload);
btnServer.addEventListener('click', callServerSave);
btnRefreshHistory.addEventListener('click', loadHistory);

loadHistory();
