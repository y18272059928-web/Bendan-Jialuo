package cn.edu.whu.schedule.importer

/**
 * Runs only in HTTPS pages under whu.edu.cn. Candidate responses stay in the
 * WebView's memory until the user explicitly taps the recognition button.
 */
internal const val WHU_CAPTURE_SCRIPT = """
(function () {
  if (window.__luojiaCaptureInstalled) return;
  window.__luojiaCaptureInstalled = true;
  window.__luojiaCaptured = [];

  const candidatePattern = /(课表|课程表|上课|curriculum|timetable|course.?table|kcmc|courseName|teachingWeek|startSection|xqj|zcd)/i;
  const remember = function (url, contentType, body) {
    try {
      if (typeof body !== 'string') body = JSON.stringify(body);
      if (!body || body.length > 1500000) return;
      const probe = String(url || '') + '\n' + body.substring(0, 80000);
      if (!candidatePattern.test(probe)) return;
      window.__luojiaCaptured.push({
        url: String(url || '').substring(0, 2000),
        contentType: String(contentType || '').substring(0, 200),
        body: body
      });
      if (window.__luojiaCaptured.length > 6) window.__luojiaCaptured.shift();
      if (window.top !== window) {
        window.top.postMessage({
          __luojiaCourseCapture: true,
          item: window.__luojiaCaptured[window.__luojiaCaptured.length - 1]
        }, '*');
      }
    } catch (_) {}
  };

  window.addEventListener('message', function (event) {
    try {
      const origin = new URL(event.origin);
      const allowed = origin.protocol === 'https:' &&
        (origin.hostname === 'whu.edu.cn' || origin.hostname.endsWith('.whu.edu.cn'));
      const item = event.data && event.data.__luojiaCourseCapture && event.data.item;
      if (!allowed || !item || window.top !== window) return;
      window.__luojiaCaptured.push(item);
      if (window.__luojiaCaptured.length > 6) window.__luojiaCaptured.shift();
    } catch (_) {}
  });

  if (window.fetch) {
    const originalFetch = window.fetch;
    window.fetch = function () {
      const args = arguments;
      return originalFetch.apply(this, args).then(function (response) {
        try {
          const copy = response.clone();
          copy.text().then(function (body) {
            remember(response.url, response.headers.get('content-type'), body);
          }).catch(function () {});
        } catch (_) {}
        return response;
      });
    };
  }

  const originalOpen = XMLHttpRequest.prototype.open;
  const originalSend = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__luojiaUrl = url;
    return originalOpen.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function () {
    this.addEventListener('load', function () {
      try {
        let body = '';
        if (!this.responseType || this.responseType === 'text') body = this.responseText;
        else if (this.responseType === 'json') body = JSON.stringify(this.response);
        remember(this.responseURL || this.__luojiaUrl, this.getResponseHeader('content-type'), body);
      } catch (_) {}
    });
    return originalSend.apply(this, arguments);
  };
})();
"""

/** Returns a JSON string containing only visible text, table cells and candidate responses. */
internal const val WHU_EXTRACT_SCRIPT = """
(function () {
  const clean = function (value, limit) {
    return String(value == null ? '' : value).replace(/\s+/g, ' ').trim().substring(0, limit);
  };
  const cleanCell = function (value, limit) {
    return String(value == null ? '' : value).replace(/[\t\r ]+/g, ' ').replace(/\n+/g, '\n').trim().substring(0, limit);
  };
  const tables = Array.from(document.querySelectorAll('table')).slice(0, 40).map(function (table) {
    const rows = Array.from(table.querySelectorAll('tr')).slice(0, 220).map(function (row) {
      return Array.from(row.children).filter(function (cell) {
        return cell.tagName === 'TH' || cell.tagName === 'TD';
      }).slice(0, 40)
        .map(function (cell) { return cleanCell(cell.innerText, 800); });
    }).filter(function (row) { return row.some(Boolean); });
    let headers = [];
    if (rows.length) {
      const firstDomRow = table.querySelector('tr');
      const hasTh = firstDomRow && firstDomRow.querySelector('th');
      if (hasTh) headers = rows.shift();
    }
    return { headers: headers, rows: rows };
  }).filter(function (table) { return table.rows.length; });

  return JSON.stringify({
    pageUrl: location.href,
    pageTitle: clean(document.title, 300),
    pageText: clean(document.body ? document.body.innerText : '', 160000),
    tables: tables,
    captures: Array.isArray(window.__luojiaCaptured) ? window.__luojiaCaptured : []
  });
})();
"""

/**
 * Both the mobile and PC portal share the signed-in HTTPS timetable endpoint.
 * Collect each teaching week from that same-origin endpoint and normalize it
 * before the data crosses the WebView boundary. No cookie, ticket or token is
 * returned to Android, and nothing is sent outside the WHU origin.
 */
internal const val WHU_PORTAL_COLLECT_SCRIPT = """
(function () {
  if (location.hostname.toLowerCase() !== 'zhlj.whu.edu.cn') return '__NOT_PORTAL__';

  // The server redirects phones back to /mobile even when /pc/index was
  // requested. Read either layout and search any same-origin iframe as well.
  const contexts = [];
  const visit = function (candidateWindow, depth) {
    if (!candidateWindow || depth > 3) return;
    try {
      const candidateDocument = candidateWindow.document;
      if (!candidateDocument || contexts.some(function (item) { return item.doc === candidateDocument; })) return;
      contexts.push({ win: candidateWindow, doc: candidateDocument });
      Array.from(candidateDocument.querySelectorAll('iframe')).forEach(function (child) {
        try { visit(child.contentWindow, depth + 1); } catch (_) {}
      });
    } catch (_) {}
  };
  visit(window, 0);
  const combinedText = contexts.map(function (item) {
    try { return item.doc.body ? item.doc.body.innerText : ''; } catch (_) { return ''; }
  }).join('\n');
  const calendarContext = contexts.find(function (item) {
    return !!item.doc.querySelector('#jcalendar_week');
  });
  const calendarDocument = calendarContext && calendarContext.doc;
  const calendar = calendarDocument && calendarDocument.querySelector('#jcalendar_week');
  if (window.__luojiaPortalCollecting) return '__PORTAL_STARTED__';

  const firstVisible = calendarDocument && (
    calendarDocument.querySelector('.calendar_day_bar li[date]') ||
    calendarDocument.querySelector('#jcalendar_week li[date]') ||
    Array.from(calendarDocument.querySelectorAll('li[date]')).find(function (item) {
      return /^\d{4}-\d{1,2}-\d{1,2}$/.test(item.getAttribute('date') || '');
    })
  );
  const dateText = firstVisible && firstVisible.getAttribute('date');
  const parts = String(dateText || '').split('-').map(Number);
  const textWeekMatch = combinedText.match(/(?:当前|教学)?\s*第\s*(\d{1,2})\s*周/);
  const attributeWeek = calendar && parseInt(calendar.getAttribute('curweek') || '', 10);
  const currentWeek = Number.isFinite(attributeWeek) ? attributeWeek :
    (textWeekMatch ? parseInt(textWeekMatch[1], 10) : NaN);
  if (!Number.isFinite(currentWeek) || currentWeek < 1 || currentWeek > 30) return '__PORTAL_WAITING__';
  const attributeTotal = calendar && parseInt(calendar.getAttribute('totalweek') || '', 10);
  const totalWeeks = Number.isFinite(attributeTotal) ? attributeTotal : 20;

  window.__luojiaPortalCollecting = true;
  window.__luojiaPortalImportResult = '__PORTAL_LOADING__';

  let firstMonday;
  if (parts.length === 3 && parts.every(function (n) { return Number.isFinite(n); })) {
    const visibleSunday = new Date(parts[0], parts[1] - 1, parts[2]);
    const firstSundayFromCalendar = new Date(visibleSunday);
    firstSundayFromCalendar.setDate(firstSundayFromCalendar.getDate() - (currentWeek - 1) * 7);
    firstMonday = new Date(firstSundayFromCalendar);
    firstMonday.setDate(firstMonday.getDate() + 1);
  } else {
    const today = new Date();
    const currentMonday = new Date(today.getFullYear(), today.getMonth(), today.getDate());
    currentMonday.setDate(currentMonday.getDate() - ((currentMonday.getDay() + 6) % 7));
    firstMonday = new Date(currentMonday);
    firstMonday.setDate(firstMonday.getDate() - (currentWeek - 1) * 7);
  }
  const firstSunday = new Date(firstMonday);
  firstSunday.setDate(firstSunday.getDate() - 1);

  const pad = function (value) { return String(value).padStart(2, '0'); };
  const iso = function (date) {
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate());
  };
  const apiDate = function (date) {
    return date.getFullYear() + '-' + (date.getMonth() + 1) + '-' + date.getDate();
  };
  const requestWeek = function (date) {
    return new Promise(function (resolve, reject) {
      try {
        const xhr = new XMLHttpRequest();
        xhr.open('GET', '/whdxIndex/getCurriculum?date=' + encodeURIComponent(apiDate(date)), true);
        xhr.withCredentials = true;
        xhr.timeout = 15000;
        xhr.onload = function () {
          if (xhr.status < 200 || xhr.status >= 300) {
            reject(new Error('HTTP ' + xhr.status));
            return;
          }
          try { resolve(JSON.parse(xhr.responseText)); }
          catch (_) { reject(new Error('返回内容不是 JSON')); }
        };
        xhr.onerror = function () { reject(new Error('网络请求失败')); };
        xhr.ontimeout = function () { reject(new Error('网络请求超时')); };
        xhr.send();
      } catch (error) { reject(error); }
    });
  };

  (async function () {
    try {
      const meetings = new Map();
      let successfulWeeks = 0;
      const safeTotal = Math.min(30, Math.max(1, totalWeeks));
      for (let week = 1; week <= safeTotal; week++) {
        const sunday = new Date(firstSunday);
        sunday.setDate(sunday.getDate() + (week - 1) * 7);
        const response = await requestWeek(sunday);
        if (!response || Number(response.result) !== 1 || !Array.isArray(response.data)) continue;
        successfulWeeks++;

        response.data.forEach(function (dayEntry, dayIndex) {
          if (!dayEntry || Number(dayEntry.day) === 8 || !Array.isArray(dayEntry.curriculumList)) return;
          // The portal renders its seven day columns Sunday through Saturday.
          const dayOfWeek = dayIndex === 0 ? 7 : dayIndex;
          dayEntry.curriculumList.forEach(function (item) {
            if (!item || !item.name) return;
            const classFlag = item.hasClass == null ? 'true' : String(item.hasClass).toLowerCase();
            if (classFlag !== 'true' && classFlag !== '1') return;
            const startPeriod = Number(item.fromClass || item.startPeriod || 0);
            const endPeriod = Number(item.endClass || item.endPeriod || startPeriod);
            if (startPeriod < 1 || startPeriod > 13) return;
            const normalized = {
              courseName: String(item.name),
              teacher: String(item.teacher || ''),
              classroom: String(item.classroom || ''),
              dayOfWeek: dayOfWeek,
              startPeriod: startPeriod,
              endPeriod: Math.max(startPeriod, Math.min(13, endPeriod)),
              weeks: []
            };
            const key = [normalized.courseName, normalized.teacher, normalized.classroom,
              normalized.dayOfWeek, normalized.startPeriod, normalized.endPeriod].join('\u001f');
            if (!meetings.has(key)) meetings.set(key, normalized);
            const saved = meetings.get(key);
            if (saved.weeks.indexOf(week) < 0) saved.weeks.push(week);
          });
        });
      }

      if (!meetings.size) {
        throw new Error('接口已响应 ' + successfulWeeks + ' 个教学周，但没有返回课程');
      }

      const semesterMatch = combinedText.match(/\d{4}\s*[-—]\s*\d{4}\s*(?:学年)?\s*第?[一二12]\s*学期/);
      window.__luojiaPortalImportResult = JSON.stringify({
        pageUrl: location.href,
        pageTitle: document.title,
        pageText: '当前第' + currentWeek + '周',
        semesterName: semesterMatch ? semesterMatch[0].replace(/\s+/g, '') : '',
        firstMonday: iso(firstMonday),
        totalWeeks: safeTotal,
        portalMeetings: Array.from(meetings.values())
      });
    } catch (error) {
      window.__luojiaPortalImportResult = '__PORTAL_ERROR__:' + String(error && error.message || error);
    } finally {
      window.__luojiaPortalCollecting = false;
    }
  })();
  return '__PORTAL_STARTED__';
})()
"""

internal const val WHU_PORTAL_READ_SCRIPT =
    "window.__luojiaPortalImportResult || '__PORTAL_LOADING__'"
