/*
 * Browser-safe code highlighting adapter.
 *
 * The previous asset was the CommonJS build of highlight.js and attempted to
 * call require() when loaded by the browser. Chat only needs the small
 * highlightElement surface, so keep this dependency-free and safe for the
 * packaged desktop build.
 */
(function () {
  function escapeHtml(value) {
    return value
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function highlightElement(element) {
    if (!element || element.dataset.fmaiHighlighted === 'true') {
      return;
    }
    element.innerHTML = escapeHtml(element.textContent || '');
    element.classList.add('hljs');
    element.dataset.fmaiHighlighted = 'true';
  }

  window.hljs = window.hljs || { highlightElement: highlightElement };
})();
