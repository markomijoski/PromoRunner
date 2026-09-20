window.amsmApp = function amsmApp() {
  return {
    modal: null,
    boardOpen: false,
    initFromQuery() {
      const open = document.body.dataset.openModal;
      const err = document.body.dataset.modalError;
      if (open === 'login' || open === 'check-email' || open === 'consent') {
        this.modal = open;
      }
      if (err === 'invalid_token') {
        this.modal = 'login';
      }
    },
    openLogin() {
      this.modal = 'login';
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
