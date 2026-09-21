window.amsmApp = function amsmApp() {
  return {
    modal: null,
    boardOpen: false,
    initFromQuery() {
      this.prefillLoginEmail();
      const authenticated = document.body.dataset.authenticated === 'true';
      const open = document.body.dataset.openModal;
      const err = document.body.dataset.modalError;

      if (authenticated && open === 'login') {
        window.location.replace('/game');
        return;
      }

      if (open === 'login' || open === 'check-email' || open === 'consent') {
        this.modal = open;
      }
      if (err === 'invalid_token') {
        this.modal = 'login';
      }
      if (this.modal === 'login') {
        this.$nextTick(() => this.prefillLoginEmail());
      }
    },
    prefillLoginEmail() {
      try {
        const saved = localStorage.getItem('amsm.email');
        if (!saved) return;
        const input = document.getElementById('login-email');
        if (input && !input.value) {
          input.value = saved;
        }
      } catch (e) {
        /* ignore */
      }
    },
    openLogin() {
      if (document.body.dataset.authenticated === 'true') {
        window.location.href = '/game';
        return;
      }
      this.modal = 'login';
      this.$nextTick(() => this.prefillLoginEmail());
    },
    openConsent() {
      this.modal = 'consent';
    },
    closeModal() {
      this.modal = null;
    },
    onPlay(authenticated) {
      if (authenticated) {
        window.location.href = '/game';
      } else {
        this.openLogin();
      }
    }
  };
};

function getCsrfToken() {
  const meta = document.querySelector('meta[name="_csrf"]');
  if (meta && meta.content) return meta.content;
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : '';
}

function getCsrfHeader() {
  const meta = document.querySelector('meta[name="_csrf_header"]');
  return (meta && meta.content) || 'X-XSRF-TOKEN';
}

window.amsmFetch = async function amsmFetch(url, options = {}) {
  const headers = Object.assign({}, options.headers || {});
  headers[getCsrfHeader()] = getCsrfToken();
  return fetch(url, Object.assign({}, options, { headers, credentials: 'same-origin' }));
};

document.addEventListener('htmx:configRequest', function (event) {
  event.detail.headers[getCsrfHeader()] = getCsrfToken();
});

document.addEventListener('htmx:afterSwap', function (event) {
  if (event.detail.target && event.detail.target.id === 'login-form-slot') {
    try {
      const saved = localStorage.getItem('amsm.email');
      const input = document.getElementById('login-email');
      if (input && saved && !input.value) {
        input.value = saved;
      }
    } catch (e) {
      /* ignore */
    }
  }
});
