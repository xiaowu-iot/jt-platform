/* 设备管理：设备台账、SIM 卡、装车记录、统计 */
(function () {
  const { ref, reactive, computed, onMounted, nextTick, watch, onUnmounted } = Vue;
  const { api, fail, ok, fmt, ago, has, qs, ElMessageBox, VehiclePicker } = JT;

  const TYPES = { JT808: '定位终端', VIDEO: '视频终端', ADAS: '主动安全终端', GB32960: '新能源车载终端', HJ1239: '排放远程终端', OTHER: '其他设备' };
  const DST = { IN_STOCK: ['库存', 'info'], INSTALLED: ['已安装', 'success'], REPAIR: ['维修中', 'warning'], SCRAPPED: ['已报废', 'danger'] };
  const SST = { IN_STOCK: ['库存', 'info'], IN_USE: ['使用中', 'success'], SUSPENDED: ['停机', 'warning'], CANCELED: ['已销户', 'danger'] };
  const DEVICE_CSV = '类型,设备号,型号,厂商,固件版本,ICCID,供应商,采购日期,保修到期,备注\n定位终端,13800138000,SIM-808,SIMU1,V2.1.0,89860400000090000001,演示供应商甲,2026-10-01,2029-10-01,';
  const SIM_CSV = 'ICCID,卡号,运营商,套餐MB,已用MB,激活日期,到期日期,备注\n89860400000090000001,1440100000001,移动,50,12.5,2026-01-01,2028-01-01,';

  /** 下载 CSV（接口要登录，用 fetch 拿到再存） */
  async function download(path, name) {
    try {
      const r = await fetch('api' + path, { credentials: 'same-origin' });
      if (!r.ok) throw new Error('导出失败：' + r.status);
      const blob = await r.blob();
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob); a.download = name + '.csv'; a.click();
      setTimeout(() => URL.revokeObjectURL(a.href), 2000);
    } catch (e) { fail(e); }
  }

  const ImportDialog = {
    props: { modelValue: Boolean, title: String, sample: String, path: String },
    emits: ['update:modelValue', 'done'],
    setup(props, { emit }) {
      const text = ref('');
      const result = ref(null);
      const loading = ref(false);
      watch(() => props.modelValue, v => { if (v) { text.value = ''; result.value = null; } });
      function pick(e) {
        const f = e.target.files[0];
        if (!f) return;
        const r = new FileReader();
        r.onload = () => { text.value = r.result; };
        r.readAsText(f, 'utf-8');
      }
      async function submit() {
        loading.value = true;
        try {
          const r = await fetch('api' + props.path, { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'text/plain;charset=UTF-8' }, body: text.value });
          const d = await r.json();
          if (!r.ok) throw new Error(d.message || r.status);
          result.value = d; emit('done');
        } catch (e) { fail(e); } finally { loading.value = false; }
      }
      return { text, result, loading, pick, submit };
    },
    template: `<el-dialog :model-value="modelValue" @update:model-value="v => $emit('update:modelValue', v)" :title="title" width="680px">
      <div class="muted" style="margin-bottom:6px">每行一条，逗号分隔（Excel 另存为 CSV UTF-8 就行），第一行表头可以留着。格式：</div>
      <pre class="dev-sample">{{ sample }}</pre>
      <div style="margin:8px 0"><input type="file" accept=".csv,.txt" @change="pick"/></div>
      <el-input v-model="text" type="textarea" :rows="8" placeholder="选文件，或者直接粘贴"/>
      <div v-if="result" class="dev-result">新增 {{ result.added }} 条<span v-if="result.updated != null">，更新 {{ result.updated }} 条</span><span v-if="result.skipped">，已存在跳过 {{ result.skipped }} 条</span>
        <div v-for="e in result.errors" :key="e" class="red">{{ e }}</div></div>
      <template #footer><el-button @click="$emit('update:modelValue', false)">关闭</el-button><el-button type="primary" :loading="loading" :disabled="!text.trim()" @click="submit">导入</el-button></template>
    </el-dialog>`,
  };

  const Devices = {
    components: { VehiclePicker, ImportDialog },
    setup() {
      const tab = ref('devices');
      const canEdit = has('VEHICLE_MANAGE');
      const stats = ref({});
      const q = reactive({ keyword: '', type: '', status: '', online: '', pending: false, page: 0, size: 20 });
      const data = ref({ items: [], total: 0 });
      const loading = ref(false);
      const sq = reactive({ keyword: '', status: '', alert: '' });
      const simList = ref([]);
      const bindList = ref([]);
      const drawer = reactive({ show: false, d: null, bindings: [], sim: null });
      const form = reactive({ show: false, f: {} });
      const inst = reactive({ show: false, vehicleId: null, reason: '' });
      const un = reactive({ show: false, to: 'IN_STOCK', reason: '' });
      const simForm = reactive({ show: false, f: {} });
      const imp = reactive({ device: false, sim: false });
      const devOptions = ref([]);

      async function loadStats() {
        try { stats.value = await api('/inventory/devices/stats'); } catch (e) { return; }
        if (tab.value === 'stats') nextTick(drawCharts);
      }
      async function load() {
        loading.value = true;
        try { data.value = await api('/inventory/devices?' + qs(q)); } catch (e) { fail(e); } finally { loading.value = false; }
      }
      async function loadSims() { try { simList.value = await api('/inventory/sims?' + qs(sq)); } catch (e) { fail(e); } }
      async function loadBindings() { try { bindList.value = await api('/inventory/devices/bindings'); } catch (e) { fail(e); } }
      const search = () => { q.page = 0; load(); };
      onMounted(() => {
        const m = /[?&]tab=(\w+)/.exec(location.hash);
        if (m) tab.value = m[1];
        if (/[?&]pending=1/.test(location.hash)) q.pending = true;
        loadStats(); load();
      });
      watch(tab, t => { if (t === 'sims') loadSims(); if (t === 'bindings') loadBindings(); if (t === 'stats') nextTick(drawCharts); });

      async function open(row) {
        try {
          const r = await api('/inventory/devices/' + row.id);
          drawer.d = r.device; drawer.bindings = r.bindings; drawer.sim = r.sim && r.sim.id ? r.sim : null; drawer.show = true;
        } catch (e) { fail(e); }
      }
      const refresh = async () => { if (drawer.show && drawer.d) await open(drawer.d); load(); loadStats(); };
      function edit(d) {
        form.f = d ? { id: d.id, deviceNo: d.deviceNo, type: d.type, terminalId: d.terminalId, makerId: d.makerId, model: d.model, hardwareVersion: d.hardwareVersion,
          firmwareVersion: d.firmwareVersion, supplier: d.supplier, purchaseDate: d.purchaseDate, warrantyUntil: d.warrantyUntil, status: d.status, remark: d.remark, installed: !!d.vehicleId }
          : { id: null, deviceNo: '', type: 'JT808', status: 'IN_STOCK' };
        form.show = true;
      }
      async function save() {
        try {
          if (form.f.id) await api('/inventory/devices/' + form.f.id, { method: 'PUT', body: form.f });
          else await api('/inventory/devices', { method: 'POST', body: form.f });
          ok('已保存'); form.show = false; refresh();
        } catch (e) { fail(e); }
      }
      async function del(d) {
        try { await ElMessageBox.confirm('从台账里删除设备 ' + d.deviceNo + '？装车记录会保留', '删除', { type: 'warning' }); } catch (e) { return; }
        try { await api('/inventory/devices/' + d.id, { method: 'DELETE' }); ok('已删除'); drawer.show = false; refresh(); } catch (e) { fail(e); }
      }
      function install() { inst.vehicleId = null; inst.reason = drawer.d.vehicleId ? '换车安装' : '安装'; inst.show = true; }
      async function doInstall() {
        try {
          await api('/inventory/devices/' + drawer.d.id + '/install', { method: 'POST', body: { vehicleId: inst.vehicleId, reason: inst.reason } });
          ok(drawer.d.type === 'JT808' ? '已装车，车辆档案的终端号已更新；这辆车原来的定位终端已拆下回库' : '已装车'); inst.show = false; refresh();
        } catch (e) { fail(e); }
      }
      function uninstall() { un.to = 'IN_STOCK'; un.reason = ''; un.show = true; }
      async function doUninstall() {
        try { await api('/inventory/devices/' + drawer.d.id + '/uninstall', { method: 'POST', body: { to: un.to, reason: un.reason } }); ok('已拆下'); un.show = false; refresh(); } catch (e) { fail(e); }
      }
      async function setStatus(to) {
        try { await api('/inventory/devices/' + drawer.d.id + '/uninstall', { method: 'POST', body: { to, reason: to === 'IN_STOCK' ? '修好入库' : '' } }); ok(); refresh(); } catch (e) { fail(e); }
      }
      async function readAttr() {
        try { await api('/inventory/devices/' + drawer.d.id + '/attributes', { method: 'POST' }); ok('已从终端读取型号、硬件/固件版本和 ICCID'); refresh(); } catch (e) { fail(e); }
      }
      async function changeSim() {
        let iccid;
        try { iccid = (await ElMessageBox.prompt('输入新卡的 ICCID（留空表示拔卡）', '换卡', { inputValue: drawer.d.iccid || '' })).value; } catch (e) { return; }
        try { await api('/inventory/devices/' + drawer.d.id + '/sim', { method: 'POST', body: { iccid: iccid || '' } }); ok(); refresh(); } catch (e) { fail(e); }
      }

      function editSim(s) {
        simForm.f = s ? Object.assign({}, s) : { id: null, iccid: '', msisdn: '', operator: '移动', planMb: 50, usedMb: 0, activateDate: null, expireDate: null, status: 'IN_STOCK', remark: '', deviceId: null };
        simForm.show = true;
        api('/inventory/devices/options').then(r => { devOptions.value = r; }).catch(() => { });
      }
      async function saveSim() {
        try {
          if (simForm.f.id) await api('/inventory/sims/' + simForm.f.id, { method: 'PUT', body: simForm.f });
          else await api('/inventory/sims', { method: 'POST', body: simForm.f });
          ok('已保存'); simForm.show = false; loadSims(); loadStats();
        } catch (e) { fail(e); }
      }
      async function delSim(s) {
        try { await ElMessageBox.confirm('删除 SIM 卡 ' + s.iccid + '？', '删除', { type: 'warning' }); } catch (e) { return; }
        try { await api('/inventory/sims/' + s.id, { method: 'DELETE' }); ok('已删除'); loadSims(); } catch (e) { fail(e); }
      }
      function quick(alert) { tab.value = 'sims'; sq.alert = alert; loadSims(); }
      function quickDev(k) {
        tab.value = 'devices'; Object.assign(q, { keyword: '', type: '', status: '', online: '', pending: false, page: 0 });
        if (k === 'pending') q.pending = true; else if (k === 'offline') { q.status = 'INSTALLED'; q.online = 'false'; } else if (k) q.status = k;
        load();
      }

      // 统计图
      const chartEls = { type: ref(null), status: ref(null), firmware: ref(null), model: ref(null) };
      const charts = [];
      function drawCharts() {
        const s = stats.value;
        if (!s.byType || !chartEls.type.value) return;
        charts.forEach(c => c.dispose()); charts.length = 0;
        const pie = (el, obj, names) => { const c = echarts.init(el); charts.push(c);
          c.setOption({ tooltip: { trigger: 'item' }, legend: { bottom: 0 }, series: [{ type: 'pie', radius: ['40%', '65%'], center: ['50%', '44%'],
            label: { formatter: '{b} {c}' }, data: Object.entries(obj).map(([k, v]) => ({ name: Array.isArray(names[k]) ? names[k][0] : names[k] || k, value: v })) }] }); };
        pie(chartEls.type.value, s.byType, TYPES);
        pie(chartEls.status.value, s.byStatus, DST);
        const bar = (el, list, color) => { const c = echarts.init(el); charts.push(c);
          c.setOption({ grid: { left: 110, right: 30, top: 10, bottom: 20 }, tooltip: {}, xAxis: { type: 'value', minInterval: 1 },
            yAxis: { type: 'category', data: list.map(x => x.name).reverse() },
            series: [{ type: 'bar', barWidth: 14, data: list.map(x => x.count).reverse(), itemStyle: { color, borderRadius: [0, 4, 4, 0] }, label: { show: true, position: 'right' } }] }); };
        bar(chartEls.firmware.value, s.byFirmware, '#2563eb');
        bar(chartEls.model.value, s.byModel, '#16a34a');
      }
      const onResize = () => charts.forEach(c => c.resize());
      onMounted(() => window.addEventListener('resize', onResize));
      onUnmounted(() => { window.removeEventListener('resize', onResize); charts.forEach(c => c.dispose()); });

      const days = s => s.daysLeft == null ? '—' : s.daysLeft < 0 ? '已过期 ' + (-s.daysLeft) + ' 天' : s.daysLeft + ' 天';
      return { tab, canEdit, stats, q, data, loading, sq, simList, bindList, drawer, form, inst, un, simForm, imp, devOptions,
        load, search, loadSims, loadBindings, open, edit, save, del, install, doInstall, uninstall, doUninstall, setStatus, readAttr, changeSim,
        editSim, saveSim, delSim, quick, quickDev, download, days, fmt, ago, has, qs, TYPES, DST, SST, DEVICE_CSV, SIM_CSV,
        typeEl: chartEls.type, statusEl: chartEls.status, firmwareEl: chartEls.firmware, modelEl: chartEls.model, loadStats };
    },
    template: `<div>
      <div class="dev-kpis">
        <div @click="quickDev('')"><small>设备总数</small><b>{{ stats.total || 0 }}</b></div>
        <div @click="quickDev('INSTALLED')"><small>已安装 · 在线率</small><b>{{ stats.installed || 0 }}<em> · {{ stats.onlineRate != null ? stats.onlineRate + '%' : '—' }}</em></b></div>
        <div @click="quickDev('IN_STOCK')"><small>库存</small><b>{{ (stats.byStatus || {}).IN_STOCK || 0 }}</b></div>
        <div @click="quickDev('REPAIR')"><small>维修中</small><b>{{ (stats.byStatus || {}).REPAIR || 0 }}</b></div>
        <div :class="{warn: stats.pending}" @click="quickDev('pending')"><small>待登记终端</small><b>{{ stats.pending || 0 }}</b></div>
        <div :class="{warn: stats.longOffline}" @click="quickDev('offline')"><small>离线超 7 天</small><b>{{ stats.longOffline || 0 }}</b></div>
        <div :class="{warn: stats.simExpiring}" @click="quick('expiring')"><small>SIM 卡 30 天内到期</small><b>{{ stats.simExpiring || 0 }}</b></div>
        <div :class="{warn: stats.simOverFlow}" @click="quick('flow')"><small>流量超 90%</small><b>{{ stats.simOverFlow || 0 }}</b></div>
      </div>
      <div class="panel">
        <el-tabs v-model="tab">
          <el-tab-pane label="设备台账" name="devices">
            <div class="toolbar">
              <el-input v-model="q.keyword" placeholder="设备号 / 车牌 / 型号 / ICCID / 固件" clearable style="width:240px" @keyup.enter="search" @clear="search"/>
              <el-select v-model="q.type" placeholder="全部类型" clearable style="width:140px" @change="search"><el-option v-for="(v,k) in TYPES" :key="k" :value="k" :label="v"/></el-select>
              <el-select v-model="q.status" placeholder="全部状态" clearable style="width:110px" @change="search"><el-option v-for="(v,k) in DST" :key="k" :value="k" :label="v[0]"/></el-select>
              <el-select v-model="q.online" placeholder="在线情况" clearable style="width:110px" @change="search"><el-option label="在线" value="true"/><el-option label="离线" value="false"/></el-select>
              <el-checkbox v-model="q.pending" @change="search">只看待登记</el-checkbox>
              <span class="grow"></span>
              <el-button @click="download('/inventory/devices/export?' + qs({keyword: q.keyword, type: q.type, status: q.status}), '设备台账')">导出</el-button>
              <template v-if="canEdit"><el-button @click="imp.device = true">批量导入</el-button><el-button type="primary" @click="edit(null)">新增设备</el-button></template>
            </div>
            <el-table :data="data.items" v-loading="loading" size="small" @row-click="open" class="clickable">
              <el-table-column label="设备号" width="175"><template #default="{row}"><b>{{ row.deviceNo }}</b><el-tag v-if="row.lastRegisterAt && !row.vehicleId" size="small" type="warning" style="margin-left:4px">待登记</el-tag></template></el-table-column>
              <el-table-column label="类型" width="110"><template #default="{row}">{{ row.typeLabel }}</template></el-table-column>
              <el-table-column label="状态" width="76"><template #default="{row}"><el-tag size="small" :type="DST[row.status][1]">{{ row.statusLabel }}</el-tag></template></el-table-column>
              <el-table-column label="车辆" width="110"><template #default="{row}">{{ row.plate || '—' }}</template></el-table-column>
              <el-table-column label="型号 / 厂商" min-width="140"><template #default="{row}">{{ row.model || '—' }}<small class="muted"> {{ row.makerId || '' }}</small></template></el-table-column>
              <el-table-column label="固件" width="100"><template #default="{row}">{{ row.firmwareVersion || '—' }}</template></el-table-column>
              <el-table-column label="SIM 卡" min-width="170"><template #default="{row}"><span v-if="row.iccid">{{ row.iccid }}</span><span v-else class="muted">—</span>
                <small v-if="row.simExpire" class="muted"> 到期 {{ row.simExpire }}</small></template></el-table-column>
              <el-table-column label="在线" width="120"><template #default="{row}"><span class="st-dot" :style="{'--c': row.online ? '#16a34a' : '#94a3b8'}">{{ row.online ? '在线' : (row.lastOnline ? ago(row.lastOnline) : '从未上线') }}</span></template></el-table-column>
              <el-table-column label="保修到期" width="100"><template #default="{row}"><span :class="{red: row.warrantyUntil && new Date(row.warrantyUntil) < new Date(Date.now() + 30*86400000)}">{{ row.warrantyUntil || '—' }}</span></template></el-table-column>
            </el-table>
            <div class="pager"><el-pagination layout="total, prev, pager, next" :total="data.total" :page-size="q.size" :current-page="q.page + 1" @current-change="p => { q.page = p - 1; load(); }"/></div>
          </el-tab-pane>

          <el-tab-pane label="SIM 卡" name="sims">
            <div class="toolbar">
              <el-input v-model="sq.keyword" placeholder="ICCID / 卡号 / 车牌 / 设备号" clearable style="width:220px" @keyup.enter="loadSims" @clear="loadSims"/>
              <el-select v-model="sq.status" placeholder="全部状态" clearable style="width:110px" @change="loadSims"><el-option v-for="(v,k) in SST" :key="k" :value="k" :label="v[0]"/></el-select>
              <el-radio-group v-model="sq.alert" size="small" @change="loadSims"><el-radio-button value="">全部</el-radio-button><el-radio-button value="expiring">30 天内到期</el-radio-button>
                <el-radio-button value="flow">流量超 90%</el-radio-button><el-radio-button value="idle">没插设备</el-radio-button></el-radio-group>
              <span class="grow"></span>
              <el-button @click="download('/inventory/sims/export', 'SIM卡')">导出</el-button>
              <template v-if="canEdit"><el-button @click="imp.sim = true">导入 / 更新流量</el-button><el-button type="primary" @click="editSim(null)">新增 SIM 卡</el-button></template>
            </div>
            <el-table :data="simList" size="small">
              <el-table-column prop="iccid" label="ICCID" width="180"/>
              <el-table-column label="卡号" width="130"><template #default="{row}">{{ row.msisdn || '—' }}</template></el-table-column>
              <el-table-column label="运营商" width="70"><template #default="{row}">{{ row.operator || '—' }}</template></el-table-column>
              <el-table-column label="本月流量" min-width="170"><template #default="{row}"><template v-if="row.planMb"><el-progress :percentage="Math.min(100, row.usedRate || 0)" :status="row.usedRate >= 90 ? 'exception' : row.usedRate >= 70 ? 'warning' : 'success'" :stroke-width="8" style="width:90px;display:inline-flex"/>
                <small class="muted"> {{ row.usedMb }}/{{ row.planMb }} MB</small></template><span v-else class="muted">—</span></template></el-table-column>
              <el-table-column label="到期" width="150"><template #default="{row}">{{ row.expireDate || '—' }} <small :class="row.daysLeft != null && row.daysLeft <= 30 ? 'red' : 'muted'">{{ row.expireDate ? days(row) : '' }}</small></template></el-table-column>
              <el-table-column label="状态" width="76"><template #default="{row}"><el-tag size="small" :type="SST[row.status][1]">{{ row.statusLabel }}</el-tag></template></el-table-column>
              <el-table-column label="设备 / 车辆" min-width="210"><template #default="{row}">{{ row.deviceNo || '—' }}<small class="muted"> {{ row.plate || '' }}</small></template></el-table-column>
              <el-table-column label="操作" width="110" v-if="canEdit"><template #default="{row}"><el-button link type="primary" @click="editSim(row)">编辑</el-button><el-button link type="danger" @click="delSim(row)">删除</el-button></template></el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="装车记录" name="bindings">
            <el-table :data="bindList" size="small">
              <el-table-column prop="deviceNo" label="设备号" width="140"/>
              <el-table-column prop="plate" label="车辆" width="110"/>
              <el-table-column label="安装" min-width="200"><template #default="{row}">{{ fmt(row.installTime, true) }}<small class="muted"> {{ row.installBy }} · {{ row.installReason }}</small></template></el-table-column>
              <el-table-column label="拆除" min-width="220"><template #default="{row}"><template v-if="row.removeTime">{{ fmt(row.removeTime, true) }}<small class="muted"> {{ row.removeBy }} · {{ row.removeReason }}</small></template><el-tag v-else size="small" type="success">在用</el-tag></template></el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="统计" name="stats">
            <div class="dev-charts">
              <div><b>设备类型</b><div ref="typeEl" class="dev-chart"></div></div>
              <div><b>设备状态</b><div ref="statusEl" class="dev-chart"></div></div>
              <div><b>定位终端固件版本</b><div ref="firmwareEl" class="dev-chart"></div></div>
              <div><b>型号</b><div ref="modelEl" class="dev-chart"></div></div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <el-drawer v-model="drawer.show" :title="drawer.d ? drawer.d.typeLabel + ' ' + drawer.d.deviceNo : ''" size="520px">
        <template v-if="drawer.d">
          <div class="dev-head"><el-tag :type="DST[drawer.d.status][1]">{{ drawer.d.statusLabel }}</el-tag>
            <span class="st-dot" :style="{'--c': drawer.d.online ? '#16a34a' : '#94a3b8'}">{{ drawer.d.online ? '在线' : '离线' }}</span>
            <span v-if="drawer.d.plate">装在 <b>{{ drawer.d.plate }}</b></span></div>
          <el-alert v-if="drawer.d.lastRegisterAt && !drawer.d.vehicleId" type="warning" :closable="false" show-icon style="margin-bottom:10px"
            :title="'这台终端 ' + fmt(drawer.d.lastRegisterAt, true) + ' 来注册过，还没有车辆档案。装到车上后，终端下次注册就能通过'"/>
          <div class="dev-grid">
            <div><small>型号</small>{{ drawer.d.model || '—' }}</div><div><small>厂商</small>{{ drawer.d.makerId || '—' }}</div>
            <div><small>终端 ID</small>{{ drawer.d.terminalId || '—' }}</div><div><small>协议版本</small>{{ drawer.d.protocolVersion ? 'JT/T 808-' + drawer.d.protocolVersion : '—' }}</div>
            <div><small>硬件版本</small>{{ drawer.d.hardwareVersion || '—' }}</div><div><small>固件版本</small>{{ drawer.d.firmwareVersion || '—' }}</div>
            <div class="full"><small>SIM 卡</small>{{ drawer.d.iccid || '—' }}<span v-if="drawer.sim"> · {{ drawer.sim.operator || '' }} {{ drawer.sim.msisdn || '' }} · 到期 {{ drawer.sim.expireDate || '—' }}</span></div>
            <div><small>供应商</small>{{ drawer.d.supplier || '—' }}</div><div><small>采购日期</small>{{ drawer.d.purchaseDate || '—' }}</div>
            <div><small>保修到期</small>{{ drawer.d.warrantyUntil || '—' }}</div><div><small>最近在线</small>{{ drawer.d.lastOnline ? fmt(drawer.d.lastOnline, true) : '—' }}</div>
            <div class="full"><small>属性读取时间</small>{{ drawer.d.attributesAt ? fmt(drawer.d.attributesAt, true) : '还没读过' }}</div>
            <div class="full" v-if="drawer.d.remark"><small>备注</small>{{ drawer.d.remark }}</div>
          </div>
          <div class="dev-actions" v-if="canEdit">
            <el-button size="small" type="primary" v-if="drawer.d.status !== 'SCRAPPED'" @click="install">{{ drawer.d.vehicleId ? '换车安装' : '装车' }}</el-button>
            <el-button size="small" v-if="drawer.d.vehicleId" @click="uninstall">拆下</el-button>
            <el-button size="small" v-if="!drawer.d.vehicleId && drawer.d.status === 'REPAIR'" @click="setStatus('IN_STOCK')">修好入库</el-button>
            <el-button size="small" v-if="!drawer.d.vehicleId && drawer.d.status === 'IN_STOCK'" @click="setStatus('REPAIR')">送修</el-button>
            <el-button size="small" v-if="drawer.d.type === 'JT808' && has('COMMAND_SEND')" :disabled="!drawer.d.online" @click="readAttr">从终端读取属性</el-button>
            <el-button size="small" @click="changeSim">换卡</el-button>
            <el-button size="small" @click="edit(drawer.d)">编辑</el-button>
            <el-button size="small" type="danger" plain v-if="!drawer.d.vehicleId" @click="del(drawer.d)">删除</el-button>
          </div>
          <div class="dev-sec">装车履历</div>
          <el-timeline v-if="drawer.bindings.length">
            <el-timeline-item v-for="b in drawer.bindings" :key="b.id" :timestamp="fmt(b.installTime, true) + (b.removeTime ? ' ~ ' + fmt(b.removeTime, true) : ' 至今')" :type="b.removeTime ? '' : 'success'">
              <b>{{ b.plate || '—' }}</b> · {{ b.installReason }}（{{ b.installBy }}）<div v-if="b.removeTime" class="muted">拆除：{{ b.removeReason }}（{{ b.removeBy }}）</div>
            </el-timeline-item>
          </el-timeline>
          <div v-else class="muted">没有装过车</div>
        </template>
      </el-drawer>

      <el-dialog v-model="form.show" :title="form.f.id ? '编辑设备' : '新增设备'" width="560px">
        <el-form label-width="90px" size="small">
          <el-form-item label="类型"><el-select v-model="form.f.type" :disabled="form.f.installed"><el-option v-for="(v,k) in TYPES" :key="k" :value="k" :label="v"/></el-select></el-form-item>
          <el-form-item label="设备号" required><el-input v-model="form.f.deviceNo" :disabled="form.f.installed" placeholder="808 终端填终端手机号；新能源、排放终端填车辆 VIN"/></el-form-item>
          <el-form-item label="型号"><el-input v-model="form.f.model" style="width:200px"/><span class="muted" style="margin:0 6px">厂商</span><el-input v-model="form.f.makerId" style="width:120px"/></el-form-item>
          <el-form-item label="终端 ID"><el-input v-model="form.f.terminalId" style="width:200px"/></el-form-item>
          <el-form-item label="版本"><el-input v-model="form.f.hardwareVersion" placeholder="硬件" style="width:140px"/><el-input v-model="form.f.firmwareVersion" placeholder="固件" style="width:140px;margin-left:8px"/></el-form-item>
          <el-form-item label="供应商"><el-input v-model="form.f.supplier"/></el-form-item>
          <el-form-item label="采购 / 保修"><el-date-picker v-model="form.f.purchaseDate" type="date" value-format="YYYY-MM-DD" placeholder="采购日期" style="width:140px"/><span style="margin:0 6px">~</span>
            <el-date-picker v-model="form.f.warrantyUntil" type="date" value-format="YYYY-MM-DD" placeholder="保修到期" style="width:140px"/></el-form-item>
          <el-form-item label="状态" v-if="!form.f.installed"><el-radio-group v-model="form.f.status"><el-radio value="IN_STOCK">库存</el-radio><el-radio value="REPAIR">维修中</el-radio><el-radio value="SCRAPPED">已报废</el-radio></el-radio-group></el-form-item>
          <el-form-item label="备注"><el-input v-model="form.f.remark"/></el-form-item>
        </el-form>
        <template #footer><el-button @click="form.show=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
      </el-dialog>

      <el-dialog v-model="inst.show" title="装车" width="440px">
        <el-form label-width="70px" size="small">
          <el-form-item label="车辆"><vehicle-picker v-model="inst.vehicleId" width="240px"/></el-form-item>
          <el-form-item label="原因"><el-input v-model="inst.reason"/></el-form-item>
        </el-form>
        <div class="muted" v-if="drawer.d && drawer.d.type === 'JT808'">定位终端：这辆车原来的定位终端会自动拆下回库，车辆档案的终端号改成这台；终端要重新注册鉴权。</div>
        <template #footer><el-button @click="inst.show=false">取消</el-button><el-button type="primary" :disabled="!inst.vehicleId" @click="doInstall">确定</el-button></template>
      </el-dialog>

      <el-dialog v-model="un.show" title="拆下设备" width="440px">
        <el-form label-width="70px" size="small">
          <el-form-item label="拆下后"><el-radio-group v-model="un.to"><el-radio value="IN_STOCK">回库存</el-radio><el-radio value="REPAIR">送修</el-radio><el-radio value="SCRAPPED">报废</el-radio></el-radio-group></el-form-item>
          <el-form-item label="原因"><el-input v-model="un.reason" placeholder="比如：车辆报废、设备故障"/></el-form-item>
        </el-form>
        <div class="muted" v-if="drawer.d && drawer.d.type === 'JT808'">拆下定位终端后这辆车就没有终端号了，在装上新终端之前不会有位置数据。</div>
        <template #footer><el-button @click="un.show=false">取消</el-button><el-button type="danger" @click="doUninstall">拆下</el-button></template>
      </el-dialog>

      <el-dialog v-model="simForm.show" :title="simForm.f.id ? '编辑 SIM 卡' : '新增 SIM 卡'" width="520px">
        <el-form label-width="90px" size="small">
          <el-form-item label="ICCID" required><el-input v-model="simForm.f.iccid" :disabled="!!simForm.f.id" maxlength="20"/></el-form-item>
          <el-form-item label="卡号"><el-input v-model="simForm.f.msisdn" style="width:200px"/><el-select v-model="simForm.f.operator" style="width:90px;margin-left:8px"><el-option v-for="o in ['移动','联通','电信']" :key="o" :value="o" :label="o"/></el-select></el-form-item>
          <el-form-item label="流量"><el-input-number v-model="simForm.f.planMb" :min="0" controls-position="right" style="width:120px"/><span class="muted" style="margin:0 6px">MB/月，已用</span>
            <el-input-number v-model="simForm.f.usedMb" :min="0" :precision="1" controls-position="right" style="width:120px"/></el-form-item>
          <el-form-item label="激活 / 到期"><el-date-picker v-model="simForm.f.activateDate" type="date" value-format="YYYY-MM-DD" style="width:140px"/><span style="margin:0 6px">~</span>
            <el-date-picker v-model="simForm.f.expireDate" type="date" value-format="YYYY-MM-DD" style="width:140px"/></el-form-item>
          <el-form-item label="状态"><el-select v-model="simForm.f.status"><el-option v-for="(v,k) in SST" :key="k" :value="k" :label="v[0]"/></el-select></el-form-item>
          <el-form-item label="插在设备"><el-select v-model="simForm.f.deviceId" filterable clearable placeholder="不插"><el-option v-for="d in devOptions" :key="d.id" :value="d.id" :label="d.deviceNo + (d.plate ? ' · ' + d.plate : '')"/></el-select></el-form-item>
          <el-form-item label="备注"><el-input v-model="simForm.f.remark"/></el-form-item>
        </el-form>
        <template #footer><el-button @click="simForm.show=false">取消</el-button><el-button type="primary" @click="saveSim">保存</el-button></template>
      </el-dialog>

      <import-dialog v-model="imp.device" title="批量导入设备" :sample="DEVICE_CSV" path="/inventory/devices/import" @done="load(); loadStats()"/>
      <import-dialog v-model="imp.sim" title="导入 SIM 卡（ICCID 已有的更新流量和到期时间）" :sample="SIM_CSV" path="/inventory/sims/import" @done="loadSims(); loadStats()"/>
    </div>`,
  };

  Object.assign(JT.Pages, { Devices });
})();
