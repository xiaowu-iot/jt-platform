/* 监控中心：运行概览、实时监控、轨迹回放、报警中心 */
(function () {
  const { ref, reactive, computed, onMounted, onUnmounted, nextTick, watch, markRaw } = Vue;
  const { state, api, fail, ok, fmt, ago, qtime, duration, has, qs, STATE, LEVEL, PLATE_COLOR, useTimer, createMap, mapSat, mapMax, carIcon, VehiclePicker, nowCN } = JT;

  /** ECharts 实例跟着组件走，窗口变大小时自动重排 */
  function useChart() {
    const charts = [];
    const onResize = () => charts.forEach(c => c.resize());
    onMounted(() => window.addEventListener('resize', onResize));
    onUnmounted(() => { window.removeEventListener('resize', onResize); charts.forEach(c => c.dispose()); });
    return el => { const c = echarts.init(el); charts.push(c); return c; };
  }
  const PALETTE = ['#2563eb', '#16a34a', '#f59e0b', '#dc2626', '#7c3aed', '#0891b2', '#db2777', '#64748b'];

  // ===================== 运行概览 =====================
  const Dashboard = {
    setup() {
      const d = ref(null);
      const alarms = ref([]);
      const elTrend = ref(null), elType = ref(null), elHour = ref(null), elFleet = ref(null);
      const init = useChart();
      let cTrend, cType, cHour, cFleet;
      async function load() {
        try {
          d.value = await api('/overview');
          alarms.value = has('ALARM_VIEW') ? (await api('/alarms?size=8')).items : [];
          await nextTick();
          draw();
        } catch (e) { fail(e); }
      }
      function draw() {
        const v = d.value;
        if (!cTrend) { cTrend = init(elTrend.value); cType = init(elType.value); cHour = init(elHour.value); cFleet = init(elFleet.value); }
        cTrend.setOption({
          color: ['#2563eb', '#f59e0b'], tooltip: { trigger: 'axis' }, legend: { data: ['里程(km)', '报警(次)'], top: 0 },
          grid: { left: 50, right: 50, top: 36, bottom: 28 },
          xAxis: { type: 'category', data: v.trend.map(t => t.day.slice(5)) },
          yAxis: [{ type: 'value', name: 'km' }, { type: 'value', name: '次' }],
          series: [{ name: '里程(km)', type: 'bar', barWidth: 22, data: v.trend.map(t => t.km), itemStyle: { borderRadius: [4, 4, 0, 0] } },
          { name: '报警(次)', type: 'line', yAxisIndex: 1, smooth: true, data: v.trend.map(t => t.alarms) }],
        });
        cType.setOption({
          color: PALETTE, tooltip: { trigger: 'item', formatter: '{b}：{c} 辆（{d}%）' }, legend: { bottom: 0 },
          series: [{ type: 'pie', radius: ['45%', '70%'], center: ['50%', '45%'], label: { formatter: '{b}\n{c}' },
            data: v.byType.map(t => ({ name: t.label, value: t.total })) }],
        });
        cHour.setOption({
          color: ['#dc2626'], tooltip: { trigger: 'axis' }, grid: { left: 40, right: 16, top: 16, bottom: 28 },
          xAxis: { type: 'category', data: v.alarmHourly.map(h => h.hour), axisLabel: { interval: 3 } },
          yAxis: { type: 'value', minInterval: 1 },
          series: [{ type: 'line', smooth: true, areaStyle: { opacity: .15 }, data: v.alarmHourly.map(h => h.count) }],
        });
        cFleet.setOption({
          color: ['#94a3b8', '#16a34a'], tooltip: { trigger: 'axis' }, legend: { data: ['车辆', '在线'], top: 0 },
          grid: { left: 110, right: 24, top: 30, bottom: 20 },
          xAxis: { type: 'value', minInterval: 1 }, yAxis: { type: 'category', data: v.byFleet.map(f => f.name) },
          series: [{ name: '车辆', type: 'bar', barGap: '-100%', barWidth: 14, data: v.byFleet.map(f => f.total), itemStyle: { borderRadius: 7 } },
          { name: '在线', type: 'bar', barWidth: 14, data: v.byFleet.map(f => f.online), itemStyle: { borderRadius: 7 } }],
        });
      }
      useTimer(load, 30000);
      const go = k => { location.hash = '/' + k; };
      return { d, alarms, elTrend, elType, elHour, elFleet, fmt, LEVEL, go };
    },
    template: `<div>
      <div class="kpis" v-if="d">
        <div class="kpi"><div class="kico"><icon name="truck" :size="22"/></div><div><div class="k">车辆总数</div><div class="v">{{ d.summary.total }}<small>辆</small></div><div class="d">行驶 {{ d.summary.running }} · 停车 {{ d.summary.stopped }}</div></div></div>
        <div class="kpi ok"><div class="kico"><icon name="online" :size="22"/></div><div><div class="k">在线车辆</div><div class="v">{{ d.summary.online }}<small>辆</small></div><div class="d">在线率 {{ d.summary.onlineRate }}% · 离线 {{ d.summary.offline }}</div></div></div>
        <div class="kpi aqua"><div class="kico"><icon name="track" :size="22"/></div><div><div class="k">今日里程</div><div class="v">{{ d.summary.todayKm }}<small>km</small></div><div class="d">行驶时长 {{ d.summary.todayRunHours }} 小时</div></div></div>
        <div class="kpi danger clickable" @click="go('alarms')"><div class="kico"><icon name="alarm" :size="22"/></div><div><div class="k">今日报警</div><div class="v">{{ d.summary.todayAlarms }}<small>次</small></div><div class="d">待处理 {{ d.summary.pendingAlarms }} 条</div></div></div>
      </div>
      <div class="grid2">
        <div class="panel"><div class="panel-title">近 7 天里程与报警</div><div ref="elTrend" class="chart"></div></div>
        <div class="panel"><div class="panel-title">车辆类型</div><div ref="elType" class="chart"></div></div>
      </div>
      <div class="grid2">
        <div class="panel"><div class="panel-title">24 小时报警趋势</div><div ref="elHour" class="chart"></div></div>
        <div class="panel"><div class="panel-title">车队在线情况</div><div ref="elFleet" class="chart"></div></div>
      </div>
      <div class="grid2" v-if="d">
        <div class="panel"><div class="panel-title">今日里程排行<small>前 10 名</small></div>
          <el-table :data="d.rank" size="small">
            <el-table-column type="index" label="#" width="48"/>
            <el-table-column prop="plate" label="车牌"/><el-table-column prop="type" label="类型" width="90"/>
            <el-table-column prop="km" label="里程(km)" width="100"/><el-table-column prop="maxSpeed" label="最高速度" width="90"/>
            <el-table-column prop="runHours" label="行驶(h)" width="80"/>
          </el-table>
        </div>
        <div class="panel"><div class="panel-title">最新报警<a class="more" @click="go('alarms')">全部 ›</a></div>
          <el-table :data="alarms" size="small">
            <el-table-column label="级别" width="70"><template #default="{row}"><el-tag size="small" :type="LEVEL[row.level].type" effect="dark">{{ row.levelName }}</el-tag></template></el-table-column>
            <el-table-column prop="plate" label="车牌" width="110"/><el-table-column prop="typeName" label="报警"/>
            <el-table-column label="时间" width="140"><template #default="{row}">{{ fmt(row.startTime, true).slice(5) }}</template></el-table-column>
          </el-table>
        </div>
      </div>
    </div>`,
  };

  // ===================== 实时监控 =====================
  const Monitor = {
    components: { VehiclePicker },
    setup() {
      const mapEl = ref(null);
      const list = ref([]);
      const fleets = ref([]);
      const keyword = ref('');
      const filter = ref('ALL');
      const fleetId = ref(null);
      const selected = ref(null);
      const detail = ref(null);
      const follow = ref(false);
      const labels = ref(true);
      let map, ll, layer, markers = new Map();
      const counts = computed(() => {
        const c = { ALL: list.value.length, RUNNING: 0, STOPPED: 0, ALARM: 0, OFFLINE: 0 };
        list.value.forEach(v => { const s = v.state === 'NEVER' ? 'OFFLINE' : v.state; c[s] = (c[s] || 0) + 1; });
        return c;
      });
      const fleetSet = computed(() => {
        if (!fleetId.value) return null;
        const ids = new Set([fleetId.value]); let added = true;
        while (added) { added = false; fleets.value.forEach(f => { if (ids.has(f.parentId) && !ids.has(f.id)) { ids.add(f.id); added = true; } }); }
        return ids;
      });
      const shown = computed(() => list.value.filter(v => {
        const s = v.state === 'NEVER' ? 'OFFLINE' : v.state;
        if (filter.value !== 'ALL' && s !== filter.value) return false;
        if (fleetSet.value && !fleetSet.value.has(v.fleetId)) return false;
        return !keyword.value || v.plate.includes(keyword.value.trim().toUpperCase());
      }));
      const fleetName = id => (fleets.value.find(f => f.id === id) || {}).name || '未分组';
      async function load() {
        try {
          list.value = await api('/vehicles/map');
          draw();
          if (selected.value) loadDetail(selected.value, false);
        } catch (e) { fail(e); }
      }
      function draw() {
        if (!map) return;
        const seen = new Set();
        for (const v of shown.value) {
          if (v.lat == null) continue;
          seen.add(v.id);
          const pos = ll(v.lat, v.lng);
          let m = markers.get(v.id);
          // 缩小看全市时车太密，车牌只在放大到 13 级以上才显示
          const icon = carIcon(v.state, v.direction, labels.value && map.getZoom() >= 13 ? v.plate : '', v.icon || v.type);
          if (!m) {
            m = L.marker(pos, { icon }).addTo(layer).on('click', () => pick(v.id));
            markers.set(v.id, m);
          } else {
            m.setLatLng(pos); m.setIcon(icon);
          }
        }
        for (const [id, m] of markers) if (!seen.has(id)) { layer.removeLayer(m); markers.delete(id); }
        if (follow.value && selected.value) {
          const v = list.value.find(x => x.id === selected.value);
          if (v && v.lat != null) map.panTo(ll(v.lat, v.lng));
        }
      }
      // 当前位置的地址：坐标变化超过约 10 米才重新查，查不到就只显示经纬度
      const address = ref('');
      let addrKey = '';
      async function loadAddress(d) {
        if (d.lat == null) { address.value = ''; addrKey = ''; return; }
        const key = d.id + ':' + d.lat.toFixed(4) + ',' + d.lng.toFixed(4);
        if (key === addrKey) return;
        if (!addrKey.startsWith(d.id + ':')) address.value = '';
        addrKey = key;
        try {
          const r = await api('/geo/address?' + qs({ lat: d.lat, lng: d.lng }));
          if (addrKey === key) address.value = r.address || '未能解析地址';
        } catch (e) { if (addrKey === key) address.value = '未能解析地址'; }
      }
      async function loadDetail(id, pan) {
        try {
          detail.value = await api('/vehicles/' + id);
          loadAddress(detail.value);
          if (pan && detail.value.lat != null) map.setView(ll(detail.value.lat, detail.value.lng), Math.max(map.getZoom(), 15));
        } catch (e) { fail(e); }
      }
      function pick(id) { selected.value = id; loadDetail(id, true); }
      // 地图放大（铺满窗口、按 Esc 还原）；看周边：放大、拉近到街道级、切卫星图
      const boxEl = ref(null), maxed = ref(false);
      let boxNode = null;
      function toggleMax(on) {
        boxNode = boxEl.value;
        maxed.value = mapMax(boxNode, map, on);
        boxNode._onEsc = () => { maxed.value = false; };
      }
      function nearBy() {
        const d = detail.value;
        if (!d || d.lat == null) return;
        toggleMax(true);
        mapSat(map, true);
        setTimeout(() => map.setView(ll(d.lat, d.lng), 17), 120);
      }
      onUnmounted(() => { if (maxed.value && boxNode) mapMax(boxNode, map, false); });
      watch([shown, labels], draw);
      onMounted(async () => {
        const r = createMap(mapEl.value); map = markRaw(r.map); ll = r.ll;
        layer = markRaw(L.layerGroup().addTo(map));
        map.on('zoomend', draw);
        try { fleets.value = await api('/fleets'); } catch (e) { /* 忽略 */ }
        const q = location.hash.split('?')[1];
        if (q && q.startsWith('id=')) pick(Number(q.slice(3)));
      });
      useTimer(load, 5000);
      const go = (k, id) => { location.hash = '/' + k + '?id=' + id; };
      return { boxEl, maxed, toggleMax, nearBy, mapEl, list, fleets, keyword, filter, fleetId, shown, counts, selected, detail, address, follow, labels, pick, fleetName, go, fmt, ago, STATE, has, PLATE_COLOR };
    },
    template: `<div class="monitor">
      <div class="mon-side">
        <div class="mon-search"><el-input v-model="keyword" placeholder="搜索车牌" clearable size="default"><template #prefix><icon name="search" :size="15"/></template></el-input>
          <el-select v-model="fleetId" placeholder="全部车队" clearable style="width:130px"><el-option v-for="f in fleets" :key="f.id" :value="f.id" :label="f.name"/></el-select></div>
        <div class="mon-tabs">
          <a v-for="k in ['ALL','RUNNING','STOPPED','ALARM','OFFLINE']" :key="k" :class="{on: filter===k}" @click="filter=k">
            <i v-if="k!=='ALL'" :style="{background: STATE[k].c}"></i>{{ k==='ALL' ? '全部' : STATE[k].t }}<b>{{ counts[k] || 0 }}</b></a>
        </div>
        <div class="mon-list">
          <div v-for="v in shown" :key="v.id" class="mon-item" :class="{on: v.id===selected}" @click="pick(v.id)">
            <i class="st" :style="{background: STATE[v.state].c}"></i>
            <div class="grow"><b>{{ v.plate }}</b><small>{{ fleetName(v.fleetId) }}</small></div>
            <div class="r"><span>{{ v.state==='OFFLINE'||v.state==='NEVER' ? STATE[v.state].t : v.speed + ' km/h' }}</span><small>{{ ago(v.gpsTime) }}</small></div>
          </div>
          <div v-if="!shown.length" class="empty">没有符合条件的车辆</div>
        </div>
      </div>
      <div ref="boxEl" class="mon-map"><div ref="mapEl" class="map"></div>
        <div class="map-tools"><el-checkbox v-model="labels" size="small">显示车牌</el-checkbox><el-checkbox v-model="follow" size="small">跟踪选中车辆</el-checkbox>
          <el-button size="small" class="map-max-btn" @click="toggleMax()">{{ maxed ? '⛶ 还原' : '⛶ 放大地图' }}</el-button></div>
        <div v-if="detail" class="mon-card">
          <div class="mc-head"><b>{{ detail.plate }}</b><el-tag size="small" :color="STATE[detail.state].c" effect="dark" style="border:none">{{ STATE[detail.state].t }}</el-tag><span class="grow"></span><a @click="detail=null;selected=null">✕</a></div>
          <div class="mc-grid">
            <div><small>车队</small>{{ detail.fleetName || '—' }}</div><div><small>类型</small>{{ detail.typeLabel }}</div>
            <div><small>速度</small>{{ detail.speed }} km/h</div><div><small>方向</small>{{ detail.direction }}°</div>
            <div><small>今日里程</small>{{ detail.todayKm }} km</div><div><small>总里程</small>{{ detail.location && detail.location.mileageKm != null ? detail.location.mileageKm + ' km' : '—' }}</div>
            <div><small>司机</small>{{ detail.driverName || '—' }}</div><div><small>终端</small>{{ detail.deviceId }} · {{ detail.protocolVersion || '—' }}</div>
            <div class="full" v-if="detail.lat != null"><small>当前位置</small>{{ address || '地址解析中…' }}<span class="mc-coord">{{ detail.lng.toFixed(5) }}, {{ detail.lat.toFixed(5) }}</span></div>
            <div class="full"><small>定位时间</small>{{ fmt(detail.gpsTime, true) }}（{{ ago(detail.gpsTime) }}）</div>
            <div class="full" v-if="detail.location"><small>状态</small>{{ detail.location.statuses.join('、') }}</div>
            <div class="full red" v-if="detail.alarms.length"><small>报警</small>{{ detail.alarms.join('、') }}</div>
          </div>
          <div class="mc-btns">
            <el-button size="small" v-if="detail.lat != null" @click="nearBy">看周边</el-button>
            <el-button size="small" @click="go('track', detail.id)">轨迹回放</el-button>
            <el-button size="small" v-if="has('COMMAND_SEND')" @click="go('commands', detail.id)">下发指令</el-button>
            <el-button size="small" v-if="has('ALARM_VIEW')" @click="go('alarms', detail.id)">报警记录</el-button>
          </div>
        </div>
        <div class="legend"><span v-for="k in ['RUNNING','STOPPED','ALARM','OFFLINE']" :key="k"><i :style="{background: STATE[k].c}"></i>{{ STATE[k].t }}</span></div>
      </div>
    </div>`,
  };

  // ===================== 轨迹回放 =====================
  const Track = {
    components: { VehiclePicker },
    setup() {
      const mapEl = ref(null);
      const vehicleId = ref(null);
      const now = nowCN();
      const start = new Date(now); start.setHours(0, 0, 0, 0);
      const range = ref([start, now]);
      const filterDrift = ref(true);
      const loading = ref(false);
      const r = ref(null);
      const idx = ref(0);
      const playing = ref(false);
      const speed = ref(8);
      let map, ll, line, done, car, stopLayer, timer, renderer, vtype, vlimit;
      /** 按这辆车的限速着色：正常绿色，接近限速黄色，超速橙红，停车灰色 */
      const speedColor = v => VehicleIcons.speedColor(v, vlimit);
      const point = computed(() => r.value && r.value.points[idx.value]);
      async function query() {
        if (!vehicleId.value) { fail(new Error('请选择车辆')); return; }
        loading.value = true; pause();
        try {
          try { const vv = await api('/vehicles/' + vehicleId.value); vtype = vv.icon || vv.type; vlimit = vv.speedLimit; } catch (e) { vtype = null; vlimit = 0; }
          r.value = await api('/tracks/' + vehicleId.value + '?' + qs({ from: qtime(range.value[0]), to: qtime(range.value[1]), filterDrift: filterDrift.value }));
          idx.value = 0; drawTrack();
          if (!r.value.points.length) JT.ElMessageBox.alert('这段时间没有轨迹', '提示');
        } catch (e) { fail(e); } finally { loading.value = false; }
      }
      function drawTrack() {
        [line, done, car, stopLayer].forEach(l => l && map.removeLayer(l));
        const pts = r.value.points.map(p => ll(p.lat, p.lng));
        if (!pts.length) return;
        // 每一段按车速着色，颜色相同的连续段合并成一条线，点多时也不卡；断档（长时间没数据又挪了很远）画灰色虚线。
        // 正常的段底下垫一条深色描边，地图花哨的地方也看得清
        line = L.layerGroup().addTo(map);
        const ps = r.value.points;
        const at = i => ({ lat: ps[i].lat, lng: ps[i].lng, sec: new Date(ps[i].t).getTime() / 1000, s: ps[i].s });
        const kinds = [];
        for (let i = 1; i < pts.length; i++) {
          kinds.push(VehicleIcons.isGap(at(i - 1), at(i)) ? 'gap' : speedColor((ps[i - 1].s + ps[i].s) / 2));
        }
        const groups = [];
        kinds.forEach((k, j) => {
          const g = groups[groups.length - 1];
          if (g && g.k === k) g.pts.push(pts[j + 1]); else groups.push({ k, pts: [pts[j], pts[j + 1]] });
        });
        let run = [];
        groups.forEach((g, j) => {
          if (g.k !== 'gap') run = run.length ? run.concat(g.pts.slice(1)) : g.pts.slice();
          if ((g.k === 'gap' || j === groups.length - 1) && run.length) {
            L.polyline(run, { color: '#0f172a', weight: 9, opacity: .55, renderer, lineJoin: 'round' }).addTo(line);
            run = [];
          }
        });
        groups.forEach(g => L.polyline(g.pts, g.k === 'gap' ? { ...VehicleIcons.GAP_STYLE, renderer }
          : { color: g.k, weight: 6, opacity: 1, renderer, lineJoin: 'round', lineCap: 'round' }).addTo(line));
        // 已经播放过的部分：中间一条白线
        done = L.polyline([], { color: '#ffffff', weight: 2, opacity: .95, renderer }).addTo(map);
        stopLayer = L.layerGroup(r.value.stops.map((s, i) => L.marker(ll(s.lat, s.lng), {
          icon: L.divIcon({ className: 'stop-icon', html: '<b>P' + (i + 1) + '</b>', iconSize: [22, 22] }),
        }).bindTooltip('停留 ' + duration(s.seconds) + '<br>' + fmt(s.start, true) + ' ~ ' + fmt(s.end, true).slice(11)))).addTo(map);
        L.marker(pts[0], { icon: L.divIcon({ className: 'se-icon s', html: '起', iconSize: [22, 22] }) }).addTo(stopLayer);
        L.marker(pts[pts.length - 1], { icon: L.divIcon({ className: 'se-icon e', html: '终', iconSize: [22, 22] }) }).addTo(stopLayer);
        car = L.marker(pts[0], { icon: carIcon('RUNNING', r.value.points[0].d, '', vtype) }).addTo(map);
        map.fitBounds(L.latLngBounds(pts), { padding: [40, 40] });
        moveTo(0);
      }
      function moveTo(i) {
        if (!r.value || !r.value.points.length) return;
        idx.value = Math.max(0, Math.min(i, r.value.points.length - 1));
        const p = r.value.points[idx.value];
        const pos = ll(p.lat, p.lng);
        car.setLatLng(pos); car.setIcon(carIcon(p.a.length ? 'ALARM' : p.s > 0 ? 'RUNNING' : 'STOPPED', p.d, '', vtype));
        done.setLatLngs(r.value.points.slice(0, idx.value + 1).map(x => ll(x.lat, x.lng)));
        if (playing.value && !map.getBounds().pad(-0.15).contains(pos)) map.panTo(pos);
      }
      function play() {
        if (!r.value || !r.value.points.length) return;
        if (idx.value >= r.value.points.length - 1) idx.value = 0;
        playing.value = true; clearInterval(timer);
        timer = setInterval(() => {
          if (idx.value >= r.value.points.length - 1) { pause(); return; }
          moveTo(idx.value + 1);
        }, Math.round(1000 / speed.value));
      }
      function pause() { playing.value = false; clearInterval(timer); }
      watch(speed, () => { if (playing.value) play(); });
      onMounted(() => {
        const m = createMap(mapEl.value); map = markRaw(m.map); ll = m.ll;
        renderer = markRaw(L.canvas({ padding: 0.5 }));
        const q = location.hash.split('?')[1];
        if (q && q.startsWith('id=')) { vehicleId.value = Number(q.slice(3)); query(); }
      });
      onUnmounted(() => clearInterval(timer));
      return { mapEl, vehicleId, range, filterDrift, loading, r, idx, point, playing, speed, query, play, pause, moveTo, fmt, duration };
    },
    template: `<div class="track">
      <div class="toolbar">
        <vehicle-picker v-model="vehicleId" />
        <el-date-picker v-model="range" type="datetimerange" start-placeholder="开始" end-placeholder="结束" style="width:360px" />
        <el-checkbox v-model="filterDrift">过滤漂移点</el-checkbox>
        <el-button type="primary" :loading="loading" @click="query">查询轨迹</el-button>
        <template v-if="r">
          <span class="muted">共 {{ r.points.length }} 个点<template v-if="r.driftCount">（过滤漂移 {{ r.driftCount }} 个）</template>，
            {{ r.meterKm != null ? '里程表 ' + r.meterKm : 'GPS ' + r.distanceKm }} km，最高 {{ r.maxSpeed }} km/h，停留 {{ r.stops.length }} 次</span>
        </template>
      </div>
      <div class="track-body">
        <div class="track-map"><div ref="mapEl" class="map"></div>
          <div v-if="r && r.points.length" class="speed-legend"><span><i style="--c:#22c55e"></i>正常</span><span><i style="--c:#eab308"></i>接近限速</span><span><i style="--c:#dc2626"></i>超速</span><span><i style="--c:#94a3b8"></i>停车</span><span><i class="gap"></i>无数据</span></div>
          <div v-if="point" class="track-info"><b>{{ fmt(point.t, true) }}</b><span>{{ point.s }} km/h</span><span>方向 {{ point.d }}°</span><span v-if="point.m != null">里程 {{ point.m }} km</span><span>{{ point.acc ? 'ACC 开' : 'ACC 关' }}</span><span v-if="point.a.length" class="red">{{ point.a.join('、') }}</span></div>
        </div>
        <div class="track-side" v-if="r && r.stops.length">
          <div class="panel-title">停留点</div>
          <div v-for="(s,i) in r.stops" :key="i" class="stop-item" @click="moveTo(r.points.findIndex(p => p.t >= s.start))">
            <b>P{{ i + 1 }}</b><div><div>{{ fmt(s.start, true).slice(5) }} ~ {{ fmt(s.end, true).slice(11) }}</div><small>{{ duration(s.seconds) }} · {{ s.accOn ? 'ACC 开' : '熄火' }}</small></div>
          </div>
        </div>
      </div>
      <div class="player" v-if="r && r.points.length">
        <el-button circle @click="playing ? pause() : play()"><icon :name="playing ? 'pause' : 'play'" :size="16"/></el-button>
        <el-slider v-model="idx" :max="r.points.length - 1" :show-tooltip="false" @input="moveTo" class="grow" />
        <span class="muted">速度</span>
        <el-select v-model="speed" style="width:90px" size="small"><el-option v-for="s in [1,4,8,16,32,64]" :key="s" :value="s" :label="s + ' 点/秒'"/></el-select>
      </div>
    </div>`,
  };

  // ===================== 报警中心 =====================
  const Alarms = {
    components: { VehiclePicker },
    setup() {
      const q = reactive({ status: 'PENDING', level: null, type: '', vehicleId: null, plate: '', range: null, page: 0, size: 20 });
      const data = ref({ items: [], total: 0 });
      const types = ref({});
      const loading = ref(false);
      const sel = ref([]);
      const dlg = reactive({ show: false, row: null, status: 'HANDLED', remark: '', confirm: true });
      const hashId = location.hash.split('?')[1];
      if (hashId && hashId.startsWith('id=')) { q.vehicleId = Number(hashId.slice(3)); q.status = ''; }
      async function load() {
        loading.value = true;
        try {
          data.value = await api('/alarms?' + qs({ status: q.status, level: q.level, type: q.type, vehicleId: q.vehicleId, plate: q.plate,
            from: q.range ? qtime(q.range[0]) : '', to: q.range ? qtime(q.range[1]) : '', page: q.page, size: q.size }));
        } catch (e) { fail(e); } finally { loading.value = false; }
      }
      onMounted(async () => { load(); try { types.value = await api('/alarms/types'); } catch (e) { /* 忽略 */ } });
      watch(() => state.alarmTick, () => { if (q.page === 0) load(); });
      function search() { q.page = 0; load(); }
      function open(row) { Object.assign(dlg, { show: true, row, status: 'HANDLED', remark: '', confirm: row.needConfirm }); }
      async function submit() {
        try { await api('/alarms/' + dlg.row.id + '/handle', { method: 'POST', body: { status: dlg.status, remark: dlg.remark, confirm: dlg.confirm } }); ok('已处理'); dlg.show = false; load(); } catch (e) { fail(e); }
      }
      async function batch(status) {
        if (!sel.value.length) return;
        try { const r = await api('/alarms/handle-batch', { method: 'POST', body: { ids: sel.value.map(x => x.id), status, remark: status === 'IGNORED' ? '批量忽略' : '批量处理' } }); ok('已处理 ' + r.count + ' 条'); load(); } catch (e) { fail(e); }
      }
      const STATUS = { PENDING: ['待处理', 'danger'], HANDLED: ['已处理', 'success'], IGNORED: ['已忽略', 'info'] };
      const track = row => { location.hash = '/track?id=' + row.vehicleId; };
      return { q, data, types, loading, sel, dlg, load, search, open, submit, batch, fmt, LEVEL, STATUS, has, track };
    },
    template: `<div>
      <div class="panel">
        <div class="toolbar">
          <el-select v-model="q.status" placeholder="全部状态" clearable style="width:110px" @change="search"><el-option label="待处理" value="PENDING"/><el-option label="已处理" value="HANDLED"/><el-option label="已忽略" value="IGNORED"/></el-select>
          <el-select v-model="q.level" placeholder="全部级别" clearable style="width:110px" @change="search"><el-option v-for="(v,k) in LEVEL" :key="k" :label="v.t" :value="Number(k)"/></el-select>
          <el-select v-model="q.type" placeholder="全部类型" clearable filterable style="width:170px" @change="search"><el-option v-for="(v,k) in types" :key="k" :label="v" :value="k"/></el-select>
          <el-input v-model="q.plate" placeholder="车牌" clearable style="width:130px" @keyup.enter="search"/>
          <el-date-picker v-model="q.range" type="datetimerange" start-placeholder="开始时间" end-placeholder="结束时间" style="width:340px" @change="search"/>
          <el-button type="primary" @click="search">查询</el-button>
          <span class="grow"></span>
          <template v-if="has('ALARM_HANDLE')"><el-button :disabled="!sel.length" @click="batch('HANDLED')">批量处理</el-button><el-button :disabled="!sel.length" @click="batch('IGNORED')">批量忽略</el-button></template>
        </div>
        <el-table :data="data.items" v-loading="loading" @selection-change="v => sel = v" size="small">
          <el-table-column type="selection" width="40"/>
          <el-table-column label="级别" width="70"><template #default="{row}"><el-tag size="small" :type="LEVEL[row.level].type" effect="dark">{{ row.levelName }}</el-tag></template></el-table-column>
          <el-table-column prop="plate" label="车牌" width="110"/>
          <el-table-column label="报警类型" min-width="150"><template #default="{row}">{{ row.typeName }}<el-tag v-if="row.source!=='TERMINAL'" size="small" type="info" style="margin-left:4px">平台</el-tag></template></el-table-column>
          <el-table-column label="开始时间" width="150"><template #default="{row}">{{ fmt(row.startTime, true) }}</template></el-table-column>
          <el-table-column label="结束时间" width="150"><template #default="{row}"><span v-if="row.endTime">{{ fmt(row.endTime, true) }}</span><el-tag v-else size="small" type="danger">持续中</el-tag></template></el-table-column>
          <el-table-column label="速度" width="80"><template #default="{row}">{{ row.speed }}</template></el-table-column>
          <el-table-column prop="detail" label="说明" min-width="140" show-overflow-tooltip/>
          <el-table-column label="状态" width="80"><template #default="{row}"><el-tag size="small" :type="STATUS[row.status][1]">{{ STATUS[row.status][0] }}</el-tag></template></el-table-column>
          <el-table-column label="处理" min-width="140" show-overflow-tooltip><template #default="{row}"><span v-if="row.handlerName">{{ row.handlerName }}：{{ row.handleRemark }}<em v-if="row.confirmed" class="muted">（已下发确认）</em></span></template></el-table-column>
          <el-table-column label="操作" width="150" fixed="right"><template #default="{row}">
            <el-button link type="primary" @click="track(row)">轨迹</el-button>
            <el-button link type="primary" v-if="row.status==='PENDING' && has('ALARM_HANDLE')" @click="open(row)">处理</el-button>
          </template></el-table-column>
        </el-table>
        <div class="pager"><el-pagination layout="total, prev, pager, next" :total="data.total" :page-size="q.size" :current-page="q.page + 1" @current-change="p => { q.page = p - 1; load(); }"/></div>
      </div>
      <el-dialog v-model="dlg.show" title="处理报警" width="440px">
        <div v-if="dlg.row" class="muted" style="margin-bottom:12px">{{ dlg.row.plate }} · {{ dlg.row.typeName }} · {{ fmt(dlg.row.startTime, true) }}</div>
        <el-form label-width="70px">
          <el-form-item label="结果"><el-radio-group v-model="dlg.status"><el-radio value="HANDLED">已处理</el-radio><el-radio value="IGNORED">误报忽略</el-radio></el-radio-group></el-form-item>
          <el-form-item label="说明"><el-input v-model="dlg.remark" type="textarea" :rows="3" placeholder="比如：已电话联系司机，提醒减速"/></el-form-item>
          <el-form-item v-if="dlg.row && dlg.row.needConfirm" label="终端"><el-checkbox v-model="dlg.confirm">下发人工确认报警（0x8203），终端清除这个报警位</el-checkbox></el-form-item>
        </el-form>
        <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button type="primary" @click="submit">确定</el-button></template>
      </el-dialog>
    </div>`,
  };

  Object.assign(JT.Pages, { Dashboard, Monitor, Track, Alarms });
})();
