window.amsmApp = function amsmApp() {
  return {
    modal: null,
    boardOpen: false,
    initFromQuery() {
      this.prefillLoginEmail();
      const authenticated = document.body.dataset.authenticated === 'true';
      const open = document.body.dataset.openModal;

      if (authenticated && (open === 'login' || open === 'register' || open === 'forgot')) {
        window.location.replace('/game');
        return;
      }

      if (open === 'login' || open === 'register' || open === 'forgot'
          || open === 'consent' || open === 'username' || open === 'account') {
        this.modal = open;
      }
      if (this.modal === 'login') {
        this.$nextTick(() => this.prefillLoginEmail());
      }
    },
    prefillLoginEmail() {
      try {
        const saved = localStorage.getItem('amsm.email');
        if (!saved) return;
        ['login-email', 'register-email'].forEach((id) => {
          const input = document.getElementById(id);
          if (input && !input.value) {
            input.value = saved;
          }
        });
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
      if (this.modal === 'username') return;
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
  const target = event.detail.target;
  if (!target) return;
  if (target.id === 'login-form-slot' || target.id === 'register-form-slot' || target.id === 'forgot-form-slot') {
    try {
      const saved = localStorage.getItem('amsm.email');
      if (!saved) return;
      target.querySelectorAll('input[name=email]').forEach((input) => {
        if (!input.value) input.value = saved;
      });
    } catch (e) {
      /* ignore */
    }
  }
});

document.addEventListener('htmx:afterRequest', function (event) {
  const redirect = event.detail.xhr && event.detail.xhr.getResponseHeader('HX-Redirect');
  if (redirect) {
    window.location.href = redirect;
  }
});
