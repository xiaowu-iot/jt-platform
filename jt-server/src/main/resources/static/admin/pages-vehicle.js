/* 车辆管理：车辆档案、车队、在线终端 */
(function () {
  const { ref, reactive, computed, onMounted } = Vue;
  const { api, fail, ok, fmt, ago, has, qs, STATE, PLATE_COLOR, useTimer, ElMessageBox } = JT;

  /** 扁平的车队列表 → 树 */
  function toTree(list) {
    const map = new Map(list.map(f => [f.id, { ...f, children: [] }]));
    const roots = [];
    map.forEach(f => { (f.parentId && map.has(f.parentId) ? map.get(f.parentId).children : roots).push(f); });
    map.forEach(f => { if (!f.children.length) delete f.children; });
    return roots;
  }

  // ===================== 车辆档案 =====================
  const Vehicles = {
    setup() {
      const q = reactive({ keyword: '', fleetId: null, type: '', state: '', page: 0, size: 20 });
      const data = ref({ items: [], total: 0 });
      const fleets = ref([]);
      const types = ref([]);
      const loading = ref(false);
      const tree = computed(() => toTree(fleets.value));
      const dlg = reactive({ show: false, id: null, f: {} });
      async function load() {
        loading.value = true;
        try { data.value = await api('/vehicles?' + qs(q)); } catch (e) { fail(e); } finally { loading.value = false; }
      }
      onMounted(async () => {
        load();
        try { fleets.value = await api('/fleets'); types.value = await api('/vehicles/types'); } catch (e) { fail(e); }
      });
      const search = () => { q.page = 0; load(); };
      function edit(row) {
        dlg.id = row ? row.id : null;
        dlg.f = row ? { plate: row.plate, plateColor: row.plateColor, vin: row.vin, type: row.type, fleetId: row.fleetId, deviceId: row.deviceId,
          simNo: row.simNo, speedLimit: row.speedLimit, driverName: row.driverName, driverPhone: row.driverPhone, remark: row.remark, enabled: row.enabled, icon: row.icon || '' }
          : { plateColor: 2, type: 'OTHER', enabled: true, icon: '' };
        dlg.show = true;
      }
      async function save() {
        try {
          await api('/vehicles' + (dlg.id ? '/' + dlg.id : ''), { method: dlg.id ? 'PUT' : 'POST', body: dlg.f });
          ok(); dlg.show = false; load();
        } catch (e) { fail(e); }
      }
      async function del(row) {
        try { await ElMessageBox.confirm('删除车辆 ' + row.plate + '？终端会无法再接入，历史轨迹保留。', '确认', { type: 'warning' }); } catch (e) { return; }
        try { await api('/vehicles/' + row.id, { method: 'DELETE' }); ok('已删除'); load(); } catch (e) { fail(e); }
      }
      const go = (k, id) => { location.hash = '/' + k + '?id=' + id; };
      // 车标预览统一用行驶状态的绿色
      const iconSvg = t => VehicleIcons.svg(t, '#16a34a', 0, 36);
      // 终端号从设备台账的库存里选（也可以直接填，保存时自动进台账）
      async function suggest(kw, cb) {
        try {
          const r = await api('/inventory/devices?' + qs({ type: 'JT808', status: 'IN_STOCK', keyword: kw || '', size: 20 }));
          cb(r.items.map(d => ({ value: d.deviceNo, model: d.model, iccid: d.iccid })));
        } catch (e) { cb([]); }
      }
      const pickDevice = d => { if (d.iccid && !dlg.f.simNo) dlg.f.simNo = d.iccid; };
      // 机器人代填：打开新建窗口、按字段填、走同一个保存
      return { q, data, fleets, tree, types, loading, dlg, load, search, edit, save, del, go, iconSvg, suggest, pickDevice, fmt, ago, has, STATE, PLATE_COLOR };
    },
    template: `<div class="panel">
      <div class="toolbar">
        <el-input v-model="q.keyword" placeholder="车牌 / 终端号 / 司机 / VIN" clearable style="width:220px" @keyup.enter="search"/>
        <el-tree-select v-model="q.fleetId" :data="tree" :props="{label:'name', value:'id'}" check-strictly clearable placeholder="全部车队" style="width:170px" @change="search"/>
        <el-select v-model="q.type" placeholder="全部类型" clearable style="width:120px" @change="search"><el-option v-for="t in types" :key="t.value" :value="t.value" :label="t.label"/></el-select>
        <el-select v-model="q.state" placeholder="全部状态" clearable style="width:110px" @change="search"><el-option label="在线" value="ONLINE"/><el-option label="离线" value="OFFLINE"/><el-option label="行驶" value="RUNNING"/><el-option label="停车" value="STOPPED"/><el-option label="报警" value="ALARM"/></el-select>
        <el-button type="primary" @click="search">查询</el-button>
        <span class="grow"></span>
        <el-button v-if="has('VEHICLE_MANAGE')" type="primary" @click="edit(null)">新增车辆</el-button>
      </div>
      <el-table :data="data.items" v-loading="loading" size="small">
        <el-table-column label="车牌" width="120"><template #default="{row}"><b>{{ row.plate }}</b><small class="muted"> {{ PLATE_COLOR[row.plateColor] }}</small></template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="{row}"><span class="st-dot" :style="{'--c': STATE[row.state].c}">{{ STATE[row.state].t }}</span></template></el-table-column>
        <el-table-column label="类型" width="110"><template #default="{row}"><span class="type-cell"><span v-html="iconSvg(row.icon || row.type)"></span>{{ row.typeLabel }}</span></template></el-table-column>
        <el-table-column prop="fleetName" label="车队" width="130"/>
        <el-table-column label="终端" min-width="150"><template #default="{row}">{{ row.deviceId }}<small class="muted" v-if="row.protocolVersion"> · {{ row.protocolVersion }}</small></template></el-table-column>
        <el-table-column prop="terminalModel" label="终端型号" width="110"/>
        <el-table-column label="司机" width="140"><template #default="{row}">{{ row.driverName }} <small class="muted">{{ row.driverPhone }}</small></template></el-table-column>
        <el-table-column label="速度" width="80"><template #default="{row}">{{ row.speed }}</template></el-table-column>
        <el-table-column label="今日里程" width="90"><template #default="{row}">{{ row.todayKm }} km</template></el-table-column>
        <el-table-column label="最后通信" width="110"><template #default="{row}">{{ ago(row.lastActive) }}</template></el-table-column>
        <el-table-column label="操作" width="200" fixed="right"><template #default="{row}">
          <el-button link type="primary" @click="go('monitor', row.id)">定位</el-button>
          <el-button link type="primary" @click="go('track', row.id)">轨迹</el-button>
          <template v-if="has('VEHICLE_MANAGE')"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="danger" @click="del(row)">删除</el-button></template>
        </template></el-table-column>
      </el-table>
      <div class="pager"><el-pagination layout="total, prev, pager, next" :total="data.total" :page-size="q.size" :current-page="q.page + 1" @current-change="p => { q.page = p - 1; load(); }"/></div>
      <el-dialog v-model="dlg.show" :title="dlg.id ? '编辑车辆' : '新增车辆'" width="620px">
        <el-form label-width="90px" class="form2">
          <el-form-item label="车牌号" required><el-input v-model="dlg.f.plate" placeholder="粤B12345"/></el-form-item>
          <el-form-item label="车牌颜色"><el-select v-model="dlg.f.plateColor"><el-option v-for="(v,k) in PLATE_COLOR" :key="k" :value="Number(k)" :label="v"/></el-select></el-form-item>
          <el-form-item label="车辆类型"><el-select v-model="dlg.f.type"><el-option v-for="t in types" :key="t.value" :value="t.value" :label="t.label"/></el-select></el-form-item>
          <el-form-item label="地图车标" class="full">
            <div class="icon-pick">
              <div class="ip" :class="{on: !dlg.f.icon}" @click="dlg.f.icon=''"><span v-html="iconSvg(dlg.f.type)"></span><small>跟随车型</small></div>
              <div class="ip" v-for="t in types" :key="t.value" :class="{on: dlg.f.icon===t.value}" @click="dlg.f.icon=t.value"><span v-html="iconSvg(t.value)"></span><small>{{ t.label }}</small></div>
            </div>
          </el-form-item>
          <el-form-item label="所属车队"><el-tree-select v-model="dlg.f.fleetId" :data="tree" :props="{label:'name', value:'id'}" check-strictly clearable/></el-form-item>
          <el-form-item label="终端号"><el-autocomplete v-model="dlg.f.deviceId" :fetch-suggestions="suggest" placeholder="从库存选，或填终端手机号" style="width:100%" @select="pickDevice">
            <template #default="{ item }"><b>{{ item.value }}</b><small class="muted"> {{ item.model || '' }} · 库存</small></template></el-autocomplete></el-form-item>
          <el-form-item label="SIM 卡号"><el-input v-model="dlg.f.simNo" placeholder="ICCID"/></el-form-item>
          <el-form-item label="VIN"><el-input v-model="dlg.f.vin" maxlength="17"/></el-form-item>
          <el-form-item label="限速"><el-input-number v-model="dlg.f.speedLimit" :min="0" :max="200" placeholder="按类型"/> <span class="muted">&nbsp;km/h，0 按类型</span></el-form-item>
          <el-form-item label="司机"><el-input v-model="dlg.f.driverName"/></el-form-item>
          <el-form-item label="司机电话"><el-input v-model="dlg.f.driverPhone"/></el-form-item>
          <el-form-item label="备注" class="full"><el-input v-model="dlg.f.remark"/></el-form-item>
          <el-form-item label="启用"><el-switch v-model="dlg.f.enabled"/></el-form-item>
        </el-form>
        <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
      </el-dialog>
    </div>`,
  };

  // ===================== 车队管理 =====================
  const Fleets = {
    setup() {
      const list = ref([]);
      const tree = computed(() => toTree(list.value));
      const dlg = reactive({ show: false, id: null, f: {} });
      async function load() { try { list.value = await api('/fleets'); } catch (e) { fail(e); } }
      onMounted(load);
      function edit(row, parentId) {
        dlg.id = row ? row.id : null;
        dlg.f = row ? { name: row.name, parentId: row.parentId, sort: row.sort, contact: row.contact, phone: row.phone, remark: row.remark } : { parentId, sort: 0 };
        dlg.show = true;
      }
      async function save() {
        try { await api('/fleets' + (dlg.id ? '/' + dlg.id : ''), { method: dlg.id ? 'PUT' : 'POST', body: dlg.f }); ok(); dlg.show = false; load(); } catch (e) { fail(e); }
      }
      async function del(row) {
        try { await ElMessageBox.confirm('删除车队 ' + row.name + '？', '确认', { type: 'warning' }); } catch (e) { return; }
        try { await api('/fleets/' + row.id, { method: 'DELETE' }); ok('已删除'); load(); } catch (e) { fail(e); }
      }
      return { list, tree, dlg, edit, save, del, has };
    },
    template: `<div class="panel">
      <div class="toolbar"><span class="muted">车队可以多级：公司 → 车队 → 班组。用户可以限定只看某些车队的车辆。</span><span class="grow"></span><el-button v-if="has('VEHICLE_MANAGE')" type="primary" @click="edit(null, null)">新增顶级车队</el-button></div>
      <el-table :data="tree" row-key="id" default-expand-all size="small">
        <el-table-column prop="name" label="名称" min-width="200"/>
        <el-table-column prop="contact" label="联系人" width="120"/><el-table-column prop="phone" label="电话" width="140"/>
        <el-table-column prop="remark" label="备注"/>
        <el-table-column label="操作" width="220" v-if="has('VEHICLE_MANAGE')"><template #default="{row}">
          <el-button link type="primary" @click="edit(null, row.id)">添加下级</el-button><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="danger" @click="del(row)">删除</el-button>
        </template></el-table-column>
      </el-table>
      <el-dialog v-model="dlg.show" :title="dlg.id ? '编辑车队' : '新增车队'" width="440px">
        <el-form label-width="80px">
          <el-form-item label="名称" required><el-input v-model="dlg.f.name"/></el-form-item>
          <el-form-item label="上级"><el-tree-select v-model="dlg.f.parentId" :data="tree" :props="{label:'name', value:'id'}" check-strictly clearable placeholder="无（顶级）"/></el-form-item>
          <el-form-item label="排序"><el-input-number v-model="dlg.f.sort" :min="0"/></el-form-item>
          <el-form-item label="联系人"><el-input v-model="dlg.f.contact"/></el-form-item>
          <el-form-item label="电话"><el-input v-model="dlg.f.phone"/></el-form-item>
          <el-form-item label="备注"><el-input v-model="dlg.f.remark"/></el-form-item>
        </el-form>
        <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
      </el-dialog>
    </div>`,
  };

  // ===================== 在线终端 =====================
  const Sessions = {
    setup() {
      const stats = ref(null);
      const list = ref([]);
      const kw = ref('');
      const shown = computed(() => list.value.filter(s => !kw.value || s.deviceId.includes(kw.value.trim())));
      async function load() {
        try { stats.value = await api('/access/stats'); list.value = await api('/access/sessions'); } catch (e) { fail(e); }
      }
      useTimer(load, 10000);
      const raw = id => { location.hash = '/raw?device=' + id; };
      const mb = b => (b / 1048576).toFixed(1) + ' MB';
      return { stats, shown, kw, fmt, ago, raw, mb };
    },
    template: `<div>
      <div class="kpis" v-if="stats">
        <div class="kpi"><div class="kico"><icon name="online" :size="22"/></div><div><div class="k">TCP 连接</div><div class="v">{{ stats.connections }}</div><div class="d">鉴权在线 {{ stats.sessions }} · 累计接入 {{ stats.accepted }}</div></div></div>
        <div class="kpi aqua"><div class="kico"><icon name="code" :size="22"/></div><div><div class="k">接收流量</div><div class="v">{{ mb(stats.bytesIn) }}</div><div class="d">发送 {{ mb(stats.bytesOut) }}</div></div></div>
        <div class="kpi ok"><div class="kico"><icon name="send" :size="22"/></div><div><div class="k">上行消息</div><div class="v">{{ stats.busPublished }}</div><div class="d">队列待处理 {{ stats.busPending }} · 拒收 {{ stats.busRejected }}</div></div></div>
        <div class="kpi warn"><div class="kico"><icon name="clock" :size="22"/></div><div><div class="k">等待应答的指令</div><div class="v">{{ stats.pendingCommands }}</div><div class="d">接入端口 {{ stats.port }}</div></div></div>
      </div>
      <div class="panel">
        <div class="toolbar"><el-input v-model="kw" placeholder="终端号" clearable style="width:200px"/><span class="muted">共 {{ shown.length }} 个会话</span></div>
        <el-table :data="shown" size="small" max-height="560">
          <el-table-column prop="deviceId" label="设备号" width="150"/><el-table-column prop="phone" label="消息头里的手机号" width="200"/>
          <el-table-column label="协议版本" width="100"><template #default="{row}">JT/T 808-{{ row.version }}</template></el-table-column>
          <el-table-column prop="remote" label="终端地址" min-width="170"/>
          <el-table-column label="上线时间" width="160"><template #default="{row}">{{ fmt(row.onlineTime, true) }}</template></el-table-column>
          <el-table-column label="最后通信" width="110"><template #default="{row}">{{ ago(row.lastActiveTime) }}</template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="{row}"><el-button link type="primary" @click="raw(row.deviceId)">报文</el-button></template></el-table-column>
        </el-table>
        <div class="muted" v-if="stats && Object.keys(stats.codec).length" style="margin-top:10px">解码丢弃：{{ stats.codec }}</div>
      </div>
    </div>`,
  };

  Object.assign(JT.Pages, { Vehicles, Fleets, Sessions, toTree });
})();
