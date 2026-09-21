(function () {
  const boards = document.querySelectorAll('.scoreboard');
  if (!boards.length) return;

  const root = boards[0];
  const highlightId = root.dataset.highlight ? Number(root.dataset.highlight) : null;

  function rankOutlineClass(rank) {
    const n = Number(rank);
    if (n === 1) return ' rank-outline-gold';
    if (n >= 2 && n <= 4) return ' rank-outline-silver';
    if (n >= 5 && n <= 10) return ' rank-outline-bronze';
    return '';
  }

  function escapeHtml(value) {
    return String(value || 'Играч')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function buildListHtml(entries) {
    if (!entries.length) {
      return '<li class="px-4 py-8 text-center text-sm text-amsm-muted">Сè уште нема резултати. Бидете први!</li>';
    }
    return entries
      .map(function (entry) {
        const highlight =
          highlightId != null && entry.userId === highlightId ? ' bg-amsm-yellow/30' : '';
        const score = Number(entry.score || 0).toLocaleString('en-US');
        const name = escapeHtml(entry.displayName);
        return (
          '<li class="flex items-center gap-3 px-4 py-3' +
          highlight +
          '">' +
          '<span class="font-display min-w-10 text-center text-xl font-bold text-amsm-ink' +
          rankOutlineClass(entry.rank) +
          '">#' +
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

  function mergeViewer(payload) {
    if (highlightId == null) {
      return Promise.resolve(payload);
    }
    const entries = payload.entries || [];
    const alreadyIn = entries.some(function (e) {
      return e.userId === highlightId;
    });
    if (alreadyIn) {
      return Promise.resolve(payload);
    }
    return fetch('/api/leaderboard/me', { credentials: 'same-origin' })
      .then(function (r) {
        return r.ok ? r.json() : null;
      })
      .then(function (me) {
        if (!me || me.rank == null || me.userId == null) {
          return payload;
        }
        if (me.userId !== highlightId) {
          return payload;
        }
        const merged = entries.slice();
        merged.push(me);
        return {
          entries: merged,
          totalPlayers: payload.totalPlayers
        };
      })
      .catch(function () {
        return payload;
      });
  }

  function applyPayload(payload) {
    if (!payload) return;
    mergeViewer(payload).then(renderEntries);
  }

  function poll() {
    fetch('/api/leaderboard', { credentials: 'same-origin' })
      .then(function (r) {
        return r.ok ? r.json() : null;
      })
      .then(applyPayload)
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
            applyPayload(JSON.parse(message.body));
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
