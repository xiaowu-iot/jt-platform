/* 车辆监控平台 · 后台公共部分：接口、格式化、图标、地图 */
(function () {
  const { reactive, ref, watch, onMounted, onUnmounted } = Vue;
  const { ElMessage, ElMessageBox } = ElementPlus;

  const state = reactive({ me: null, ready: false, page: '', hash: '', options: { map: {} }, alarmTick: 0, latestAlarm: null });

  async function api(path, { method = 'GET', body, form } = {}) {
    const opt = { method, headers: {}, credentials: 'same-origin' };
    if (form) {
      opt.body = form;
    } else if (body !== undefined) {
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch('api' + path, opt);
    const ct = resp.headers.get('content-type') || '';
    const data = ct.includes('json') ? await resp.json() : null;
    if (resp.status === 401) { state.me = null; throw new Error((data && data.message) || '请先登录'); }
    if (!resp.ok) {
      const err = new Error((data && (data.message || data.error)) || '请求失败：' + resp.status);
      err.code = data && data.code;
      throw err;
    }
    return data;
  }
  const fail = e => ElMessage.error(e.message || '操作失败');
  const ok = msg => ElMessage.success(msg || '已保存');
  const pad = n => String(n).padStart(2, '0');
  function fmt(s, withSec) {
    if (!s) return '';
    const d = new Date(s);
    if (isNaN(d)) return s;
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + (withSec ? ':' + pad(d.getSeconds()) : '');
  }
  /** 平台按北京时间返回不带时区的时间，页面上统一按北京时间计算 */
  const nowCN = () => new Date(Date.now() + (480 + new Date().getTimezoneOffset()) * 60000);
  function ago(s) {
    if (!s) return '—';
    // 数字是真正的时间戳（毫秒），字符串是北京时间
    const sec = Math.max(0, Math.round(((typeof s === 'number' ? Date.now() : nowCN()) - new Date(s)) / 1000));
    return sec < 60 ? sec + ' 秒前' : sec < 3600 ? Math.round(sec / 60) + ' 分钟前' : sec < 86400 ? Math.round(sec / 3600) + ' 小时前' : fmt(s);
  }
  function qtime(d) {
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
  }
  function duration(sec) {
    if (sec == null) return '';
    const h = Math.floor(sec / 3600), m = Math.floor(sec % 3600 / 60);
    return h ? h + ' 小时 ' + m + ' 分' : m ? m + ' 分 ' + (sec % 60) + ' 秒' : sec + ' 秒';
  }
  const has = p => !!(state.me && state.me.permissions.includes(p));
  const qs = o => Object.entries(o).filter(([, v]) => v !== undefined && v !== null && v !== '').map(([k, v]) => k + '=' + encodeURIComponent(v)).join('&');

  const STATE = {
    RUNNING: { t: '行驶', c: '#16a34a' }, STOPPED: { t: '停车', c: '#2563eb' }, ALARM: { t: '报警', c: '#dc2626' },
    OFFLINE: { t: '离线', c: '#94a3b8' }, NEVER: { t: '未上线', c: '#cbd5e1' },
  };
  const LEVEL = { 1: { t: '一般', type: 'info' }, 2: { t: '重要', type: 'warning' }, 3: { t: '紧急', type: 'danger' } };
  const PLATE_COLOR = { 1: '蓝', 2: '黄', 3: '黑', 4: '白', 5: '绿', 9: '其他', 0: '未上牌' };

  // ---------- 线条图标（24×24，描边） ----------
  const ICONS = {
    monitor: 'M3 4h18v12H3zM8 21h8M12 16v5M6 12l3-3 3 2 4-4 2 2',
    pin: 'M12 21s-6-5.5-6-11a6 6 0 1 1 12 0c0 5.5-6 11-6 11zM12 12a2 2 0 1 0 0-4 2 2 0 0 0 0 4',
    calendar: 'M4 5h16v15H4zM4 10h16M8 3v4M16 3v4',
    list: 'M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01',
    grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
    map: 'M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3zM9 3v15M15 6v15',
    track: 'M5 19c3-6 6-2 9-8s4-4 5-6M5 19a2 2 0 1 0 0 .01M19 5a2 2 0 1 0 0 .01',
    alarm: 'M12 3l9.5 17H2.5zM12 10v4M12 17.5v.5',
    truck: 'M2 6h11v10H2zM13 9h4.5l3.5 3.5V16h-8M6 19.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM17 19.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z',
    team: 'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM2 21a7 7 0 0 1 14 0M16 3.5a4 4 0 0 1 0 7.5M18 14a6 6 0 0 1 4 7',
    online: 'M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5h0M2 9a14 14 0 0 1 20 0',
    send: 'M22 2L11 13M22 2l-7 20-4-9-9-4z',
    log: 'M6 3h9l4 4v14H6zM14 3v5h5M9 12h7M9 16h7',
    code: 'M8 7l-5 5 5 5M16 7l5 5-5 5M14 4l-4 16',
    bell: 'M6 16V11a6 6 0 0 1 12 0v5l2 2H4zM10 21h4',
    user: 'M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9zM4 21a8 8 0 0 1 16 0',
    role: 'M12 2l8 3v6c0 5-3.4 9.4-8 11-4.6-1.6-8-6-8-11V5zM9 12l2 2 4-4',
    gear: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1',
    clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2',
    screen: 'M2 4h20v13H2zM7 21h10M12 17v4',
    chart: 'M3 3v18h18M7 15l4-4 3 3 5-6',
    fence: 'M4 4l6 2 7-2 3 7-3 9-8-2-5 2zM10 6v12',
    camera: 'M3 7h4l2-3h6l2 3h4v13H3zM12 17a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
    video: 'M3 6h12v12H3zM15 10l6-3v10l-6-3',
    shield: 'M12 2l8 3v6c0 5-3.4 9.4-8 11-4.6-1.6-8-6-8-11V5z',
    upload: 'M7 18a5 5 0 0 1-.7-9.95A6 6 0 0 1 18 8a4.5 4.5 0 0 1 .5 9M12 12v9M8.5 15.5L12 12l3.5 3.5',
    link: 'M10 14a5 5 0 0 0 7 0l3-3a5 5 0 0 0-7-7l-1 1M14 10a5 5 0 0 0-7 0l-3 3a5 5 0 0 0 7 7l1-1',
    battery: 'M3 7h16v10H3zM21 10v4M7 10v4M11 10v4',
    record: 'M5 3h14v18H5zM9 7h6M9 11h6M9 15h3',
    play: 'M6 4l14 8-14 8z',
    pause: 'M7 4h3v16H7zM14 4h3v16h-3z',
    search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-5-5',
    refresh: 'M20 11a8 8 0 0 0-14.9-3M4 13a8 8 0 0 0 14.9 3M4 4v4h4M20 20v-4h-4',
    ai: 'M12 3l1.8 4.7 4.7 1.8-4.7 1.8L12 16l-1.8-4.7-4.7-1.8 4.7-1.8zM19 14l.9 2.1L22 17l-2.1.9L19 20l-.9-2.1L16 17l2.1-.9zM5 15l.6 1.4L7 17l-1.4.6L5 19l-.6-1.4L3 17l1.4-.6z',
    locate: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM12 1v3M12 20v3M1 12h3M20 12h3',
  };
  const Icon = {
    props: { name: String, size: { type: Number, default: 18 } },
    template: `<svg :width="size" :height="size" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" class="ico"><path :d="ICONS[name] || ''"/></svg>`,
    setup() { return { ICONS }; },
  };

  // ---------- 坐标：后端给的都是 GCJ-02（高德）；天地图要 WGS84，在这里转回去 ----------
  const PI = Math.PI, A = 6378245.0, EE = 0.00669342162296594323;
  function tLat(x, y) { let r = -100 + 2 * x + 3 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x)); r += (20 * Math.sin(6 * x * PI) + 20 * Math.sin(2 * x * PI)) * 2 / 3; r += (20 * Math.sin(y * PI) + 40 * Math.sin(y / 3 * PI)) * 2 / 3; r += (160 * Math.sin(y / 12 * PI) + 320 * Math.sin(y * PI / 30)) * 2 / 3; return r; }
  function tLng(x, y) { let r = 300 + x + 2 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x)); r += (20 * Math.sin(6 * x * PI) + 20 * Math.sin(2 * x * PI)) * 2 / 3; r += (20 * Math.sin(x * PI) + 40 * Math.sin(x / 3 * PI)) * 2 / 3; r += (150 * Math.sin(x / 12 * PI) + 300 * Math.sin(x / 30 * PI)) * 2 / 3; return r; }
  function wgs2gcj(lat, lng) {
    let dLat = tLat(lng - 105, lat - 35), dLng = tLng(lng - 105, lat - 35);
    const rad = lat / 180 * PI; let m = Math.sin(rad); m = 1 - EE * m * m; const s = Math.sqrt(m);
    dLat = dLat * 180 / ((A * (1 - EE)) / (m * s) * PI); dLng = dLng * 180 / (A / s * Math.cos(rad) * PI);
    return [lat + dLat, lng + dLng];
  }
  function gcj2wgs(lat, lng) { let w = [lat, lng]; for (let i = 0; i < 5; i++) { const g = wgs2gcj(w[0], w[1]); w = [w[0] - (g[0] - lat), w[1] - (g[1] - lng)]; } return w; }

  /** 底图：高德瓦片（GCJ-02，不需要 key）或天地图（WGS84，需要 key） */
  function baseLayers(map) {
    const m = state.options.map || {};
    if (m.provider === 'tianditu' && m.tiandituKey) {
      const t = 'https://t{s}.tianditu.gov.cn/', k = '&tk=' + m.tiandituKey, sub = ['0', '1', '2', '3', '4', '5', '6', '7'];
      const vec = L.layerGroup([L.tileLayer(t + 'vec_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=vec&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}' + k, { subdomains: sub, maxZoom: 18 }),
      L.tileLayer(t + 'cva_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=cva&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}' + k, { subdomains: sub, maxZoom: 18 })]);
      const img = L.layerGroup([L.tileLayer(t + 'img_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=img&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}' + k, { subdomains: sub, maxZoom: 18 }),
      L.tileLayer(t + 'cia_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=cia&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}' + k, { subdomains: sub, maxZoom: 18 })]);
      vec.addTo(map);
      L.control.layers({ '地图': vec, '卫星': img }, null, { position: 'topright' }).addTo(map);
      map._jtBase = { vec, img };
      return { wgs: true };
    }
    const vec = L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', { subdomains: '1234', maxZoom: 18 });
    // 卫星图：webst02 在有些网络下连不上（瓦片一直空着），只用 01/03/04，单块加载失败再换一个子域名重试一次
    const st = style => L.tileLayer('https://webst0{s}.is.autonavi.com/appmaptile?style=' + style + '&x={x}&y={y}&z={z}', { subdomains: '134', maxZoom: 18 })
      .on('tileerror', e => { const t = e.tile; if (!t.dataset.retry) { t.dataset.retry = 1; t.src = t.src.replace(/webst0\d/, 'webst03'); } });
    const img = L.layerGroup([st(6), st(8)]);
    vec.addTo(map);
    L.control.layers({ '地图': vec, '卫星': img }, null, { position: 'topright' }).addTo(map);
    map._jtBase = { vec, img };
    return { wgs: false };
  }
  /** 切卫星图 / 地图（右上角的图层切换会跟着变） */
  function mapSat(map, on) {
    const b = map._jtBase;
    if (!b) return;
    if (on && !map.hasLayer(b.img)) { map.removeLayer(b.vec); b.img.addTo(map); }
    if (!on && !map.hasLayer(b.vec)) { map.removeLayer(b.img); b.vec.addTo(map); }
  }
  /** 地图放大：地图所在的框铺满整个窗口，盖住菜单和列表；再调一次或按 Esc 还原。返回放大后的状态 */
  function mapMax(box, map, on) {
    const want = on == null ? !box.classList.contains('map-maxed') : on;
    box.classList.toggle('map-maxed', want);
    if (want && !box._esc) {
      box._esc = e => { if (e.key === 'Escape') { mapMax(box, map, false); if (box._onEsc) box._onEsc(); } };
      document.addEventListener('keydown', box._esc);
    } else if (!want && box._esc) {
      document.removeEventListener('keydown', box._esc);
      box._esc = null;
    }
    setTimeout(() => map.invalidateSize(), 60);
    return want;
  }
  /** 新建地图，返回 { map, ll }，ll 把后端给的 GCJ-02 坐标转成当前底图的坐标 */
  function createMap(el, opts = {}) {
    const m = state.options.map || {};
    const center = [m.centerLat || 22.585, m.centerLng || 114.03], zoom = m.zoom || 11;
    const map = L.map(el, Object.assign({ zoomControl: true, attributionControl: false }, opts)).setView(center, zoom);
    const b = baseLayers(map);
    const ll = (lat, lng) => b.wgs ? gcj2wgs(lat, lng) : [lat, lng];
    if (map.zoomControl) homeControl(center, zoom).addTo(map);
    return { map, ll };
  }
  /** 全景：缩放到地图上的全部内容（车辆、线路、点位、围栏、轨迹），什么都没有就回到平台默认的中心 */
  function homeControl(center, zoom) {
    const Home = L.Control.extend({
      options: { position: 'topleft' },
      onAdd(map) {
        const box = L.DomUtil.create('div', 'leaflet-bar jt-home');
        const a = L.DomUtil.create('a', '', box);
        a.href = '#';
        a.title = '全景：显示地图上的全部内容';
        a.setAttribute('role', 'button');
        a.textContent = '全';
        L.DomEvent.disableClickPropagation(box);
        L.DomEvent.on(a, 'click', e => {
          L.DomEvent.preventDefault(e);
          const all = L.latLngBounds([]);
          map.eachLayer(l => {
            if (l instanceof L.TileLayer || l instanceof L.DivOverlay || l instanceof L.LayerGroup) return;
            try {
              if (l.getBounds) { const x = l.getBounds(); if (x.isValid()) all.extend(x); } else if (l.getLatLng) all.extend(l.getLatLng());
            } catch (err) { /* 还没算好位置的图层跳过 */ }
          });
          if (all.isValid()) map.fitBounds(all.pad(0.05), { maxZoom: 16 });
          else map.setView(center, zoom);
        });
        return box;
      },
    });
    return new Home();
  }
  /** 车辆图标：带方向的箭头，颜色表示状态 */
  /** 地图车标：按车型画俯视小车，颜色表示状态，跟着方向转 */
  function carIcon(st, dir, label, type) {
    const c = (STATE[st] || STATE.OFFLINE).c;
    const html = `<div class="car${st === 'ALARM' ? ' alarm' : ''}" style="--c:${c}">${VehicleIcons.svg(type, c, dir, 34)}${label ? '<span>' + label + '</span>' : ''}</div>`;
    return L.divIcon({ className: 'car-icon', html, iconSize: [34, 34], iconAnchor: [17, 17] });
  }

  /** 定时刷新，离开页面自动停 */
  function useTimer(fn, ms) {
    let t = null;
    onMounted(() => { fn(); t = setInterval(fn, ms); });
    onUnmounted(() => clearInterval(t));
  }

  /** 车辆下拉框：按车牌搜 */
  const VehiclePicker = {
    props: { modelValue: [Number, String], placeholder: { type: String, default: '选择车辆' }, width: { type: String, default: '200px' } },
    emits: ['update:modelValue', 'pick'],
    setup(props, { emit }) {
      const opts = ref([]);
      const loading = ref(false);
      async function search(k) {
        loading.value = true;
        try { opts.value = (await api('/vehicles?' + qs({ keyword: k, size: 30 }))).items; } catch (e) { /* 忽略 */ } finally { loading.value = false; }
      }
      async function ensure(id) {
        if (!id || opts.value.some(o => o.id === id)) return;
        try { opts.value = [await api('/vehicles/' + id), ...opts.value]; } catch (e) { /* 忽略 */ }
      }
      onMounted(async () => { await search(''); ensure(props.modelValue); });
      watch(() => props.modelValue, ensure);
      function change(v) { emit('update:modelValue', v); emit('pick', opts.value.find(o => o.id === v)); }
      return { opts, loading, search, change, STATE };
    },
    template: `<el-select :model-value="modelValue" filterable remote :remote-method="search" :loading="loading" :placeholder="placeholder" :style="{width}" @change="change" clearable>
      <el-option v-for="o in opts" :key="o.id" :value="o.id" :label="o.plate"><span>{{ o.plate }}</span><span class="opt-sub" :style="{color: (STATE[o.state]||{}).c}">{{ (STATE[o.state]||{}).t }}</span></el-option>
    </el-select>`,
  };

  /** 极简 Markdown：标题、加粗、列表、段落，AI 回答和日报用。先转义再替换，不会注入 HTML */
  function md(text) {
    const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const inline = s => esc(s).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/`(.+?)`/g, '<code>$1</code>');
    const out = [];
    let list = null;
    const close = () => { if (list) { out.push('</' + list + '>'); list = null; } };
    for (const raw of String(text || '').split('\n')) {
      const line = raw.trimEnd();
      let m;
      if ((m = line.match(/^(#{1,6})\s+(.*)$/))) { close(); const n = Math.min(6, m[1].length + 1); out.push('<h' + n + '>' + inline(m[2]) + '</h' + n + '>'); }
      else if ((m = line.match(/^\s*[-*]\s+(.*)$/))) { if (list !== 'ul') { close(); out.push('<ul>'); list = 'ul'; } out.push('<li>' + inline(m[1]) + '</li>'); }
      else if ((m = line.match(/^\s*\d+[.、]\s*(.*)$/))) { if (list !== 'ol') { close(); out.push('<ol>'); list = 'ol'; } out.push('<li>' + inline(m[1]) + '</li>'); }
      else if (!line.trim()) { close(); }
      else { close(); out.push('<p>' + inline(line) + '</p>'); }
    }
    close();
    return out.join('');
  }

  window.JT = { state, md, api, fail, ok, fmt, nowCN, ago, qtime, duration, has, qs, STATE, LEVEL, PLATE_COLOR, Icon, useTimer, createMap, mapSat, mapMax, carIcon, wgs2gcj, gcj2wgs, VehiclePicker, ElMessageBox, Pages: {} };
})();
