// 다크/라이트 모드 토글. 선택은 localStorage 에 저장해 다음 방문에도 유지.
(function () {
  var btn = document.getElementById('theme-toggle');
  if (!btn) return;
  btn.addEventListener('click', function () {
    var root = document.documentElement;
    var isDark = root.classList.toggle('dark');
    try {
      localStorage.setItem('theme', isDark ? 'dark' : 'light');
    } catch (e) { /* 저장 불가 환경 무시 */ }
  });
})();
