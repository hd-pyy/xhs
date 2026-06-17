// XHS Downloader Web 版前端 —— 单文件 vanilla JS。
// 调用 /api/parse 和 /api/download,把结果渲染到 DOM。

const $ = (id) => document.getElementById(id);
const textInput = $('text-input');
const btnParse = $('btn-parse');
const btnDownload = $('btn-download');
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

async function callDownload() {
  const text = textInput.value.trim();
  if (!text) {
    setStatus('请先粘贴小红书分享文案或链接', 'error');
    return;
  }
  setStatus('下载中,请稍候(可能耗时 30s+)…');
  btnParse.disabled = true;
  btnDownload.disabled = true;
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
      json.ok ? `下载完成:${(json.savedFiles ?? []).length} 个文件` : '下载未成功,请查看控制台',
      json.ok ? 'ok' : 'error',
    );
    await loadHistory();
  } catch (e) {
    setStatus('下载异常: ' + (e?.message ?? e), 'error');
  } finally {
    btnParse.disabled = false;
    btnDownload.disabled = false;
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
btnRefreshHistory.addEventListener('click', loadHistory);

loadHistory();
