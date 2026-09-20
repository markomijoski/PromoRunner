(function () {
  const boards = document.querySelectorAll('.scoreboard');
  if (!boards.length) return;

  const root = boards[0];
  const highlightId = root.dataset.highlight ? Number(root.dataset.highlight) : null;

  function buildListHtml(entries) {
    if (!entries.length) {
      return '<li class="px-4 py-8 text-center text-sm text-amsm-muted">Сè уште нема резултати. Бидете први!</li>';
    }
    return entries
      .map(function (entry) {
        const highlight =
          highlightId != null && entry.userId === highlightId ? ' bg-amsm-yellow/30' : '';
        const score = Number(entry.score || 0).toLocaleString('en-US');
        const name = String(entry.displayName || 'Играч')
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;');
        return (
          '<li class="flex items-center gap-3 px-4 py-3' +
          highlight +
          '">' +
          '<span class="font-display w-8 text-xl font-bold text-amsm-ink">#' +
          entry.rank +
          '</span>' +
          '<span class="min-w-0 flex-1 truncate font-medium">' +
          name +
          '</span>' +
          '<span class="font-display text-lg font-bold tabular-nums">' +
          score +
          '</span>' +
          '</li>'
        );
      })
      .join('');
  }

  function renderEntries(payload) {
    const entries = payload.entries || [];
    const total = payload.totalPlayers != null ? payload.totalPlayers : entries.length;
    const listHtml = buildListHtml(entries);

    document.querySelectorAll('.scoreboard').forEach(function (board) {
      const count = board.querySelector('.scoreboard-count');
      if (count) count.textContent = total + ' играчи';
      const list = board.querySelector('.scoreboard-list');
      if (list) list.innerHTML = listHtml;
      if (highlightId != null) board.dataset.highlight = String(highlightId);
    });
  }

  function poll() {
    fetch('/api/leaderboard', { credentials: 'same-origin' })
      .then(function (r) {
        return r.ok ? r.json() : null;
      })
      .then(function (data) {
        if (data) renderEntries(data);
      })
      .catch(function () {});
  }

  let pollTimer = setInterval(poll, 10000);

  function ensurePolling() {
    if (pollTimer == null) {
      pollTimer = setInterval(poll, 10000);
    }
  }

  function stopPolling() {
    if (pollTimer != null) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
  }

  function connectStomp() {
    if (typeof SockJS === 'undefined' || typeof StompJs === 'undefined') {
      return;
    }
    const client = new StompJs.Client({
      webSocketFactory: function () {
        return new SockJS('/ws');
      },
      reconnectDelay: 5000,
      onConnect: function () {
        stopPolling();
        client.subscribe('/topic/leaderboard', function (message) {
          try {
            renderEntries(JSON.parse(message.body));
          } catch (e) {}
        });
      },
      onDisconnect: function () {
        ensurePolling();
      },
      onStompError: function () {
        ensurePolling();
      },
      onWebSocketClose: function () {
        ensurePolling();
      }
    });
    client.activate();
  }

  const sock = document.createElement('script');
  sock.src = 'https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js';
  sock.onload = function () {
    const stomp = document.createElement('script');
    stomp.src = 'https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js';
    stomp.onload = connectStomp;
    document.head.appendChild(stomp);
  };
  document.head.appendChild(sock);
})();
