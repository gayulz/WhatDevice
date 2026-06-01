// WhatDevice 검색 — 바닐라 JS. data.js 의 window.DEVICES 를 식별자/기종명/한글명으로
// 양방향 실시간 필터링한다. 외부 라이브러리 없음.
(function () {
  var input = document.getElementById('q');
  var results = document.getElementById('results');
  if (!input || !results || !window.DEVICES) return;

  var DEVICES = window.DEVICES;

  // 검색 매칭용 정규화: 소문자 + 공백 제거. (iPhone15,2 / iphone 15 2 / 아이폰15,2 모두 매칭)
  function norm(s) {
    return (s || '').toLowerCase().replace(/\s+/g, '');
  }

  // 각 기기에 검색용 합성 문자열을 미리 만들어 둔다.
  DEVICES.forEach(function (d) {
    d._h = norm(d.i) + '' + norm(d.n) + '' + norm(d.k);
  });

  function escapeHtml(s) {
    return (s || '').replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  function card(d) {
    // .card-head: 식별자(좌) + 카테고리 배지(우) 한 줄 양쪽 정렬 wrapper
    return '<a class="card" href="device/' + encodeURIComponent(d.s) + '.html">'
      + '<span class="card-head">'
      +   '<span class="id">' + escapeHtml(d.i) + '</span>'
      +   '<span class="cat">' + escapeHtml(d.c) + '</span>'
      + '</span>'
      + '<span class="nm">' + escapeHtml(d.n) + '</span>'
      + '</a>';
  }

  function render(list) {
    if (list.length === 0) {
      results.innerHTML = '<p class="empty">검색 결과가 없습니다. 식별자(예: iPhone15,2)나 기종명(예: iPhone 14 Pro)으로 다시 검색해 보세요.</p>';
      return;
    }
    // 너무 많은 노드 생성을 막기 위해 최대 300개까지만 렌더(전체 160개라 사실상 전부).
    var html = list.slice(0, 300).map(card).join('');
    results.innerHTML = html;
  }

  function search(q) {
    var nq = norm(q);
    if (nq === '') {
      render(DEVICES); // 빈 검색어면 전체 목록(콘텐츠 양 확보 + 탐색 가능)
      return;
    }
    var matched = DEVICES.filter(function (d) {
      return d._h.indexOf(nq) !== -1;
    });
    render(matched);
  }

  // 입력 디바운스
  var timer = null;
  input.addEventListener('input', function () {
    clearTimeout(timer);
    var v = input.value;
    timer = setTimeout(function () { search(v); }, 80);
  });

  // 최초 렌더: 전체 목록
  render(DEVICES);

  // URL ?q= 로 들어온 경우 자동 검색
  try {
    var params = new URLSearchParams(window.location.search);
    var pre = params.get('q');
    if (pre) { input.value = pre; search(pre); }
  } catch (e) { /* 무시 */ }
})();
